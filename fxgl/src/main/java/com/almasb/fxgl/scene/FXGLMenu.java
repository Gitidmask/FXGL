/*
 * FXGL - JavaFX Game Library. The MIT License (MIT).
 * Copyright (c) AlmasB (almaslvl@gmail.com).
 * See LICENSE for details.
 */
package com.almasb.fxgl.scene;

import com.almasb.fxgl.app.ApplicationMode;
import com.almasb.fxgl.app.FXGL;
import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.MenuEventHandler;
import com.almasb.fxgl.core.logging.Logger;
import com.almasb.fxgl.gameplay.Achievement;
import com.almasb.fxgl.gameplay.GameDifficulty;
import com.almasb.fxgl.input.InputModifier;
import com.almasb.fxgl.input.Trigger;
import com.almasb.fxgl.input.TriggerView;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.saving.SaveFile;
import com.almasb.fxgl.scene.menu.MenuType;
import com.almasb.fxgl.ui.FXGLSpinner;
import com.almasb.fxgl.util.Language;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.almasb.fxgl.app.FXGL.getSettings;
import static com.almasb.fxgl.app.FXGL.localizedStringProperty;

/**
 * This is a base class for main/game menus. It provides several
 * convenience methods for those who just want to extend an existing menu.
 * It also allows for implementors to build menus from scratch. Freshly
 * build menus can interact with FXGL by calling fire* methods.
 *
 * Both main and game menus <strong>should</strong> have the following items:
 * <ul>
 *     <li>Background</li>
 *     <li>Title</li>
 *     <li>Version</li>
 *     <li>Profile name</li>
 *     <li>Menu Body</li>
 *     <li>Menu Content</li>
 * </ul>
 *
 * However, in reality a menu can contain anything.
 *
 * @author Almas Baimagambetov (AlmasB) (almaslvl@gmail.com)
 */
public abstract class FXGLMenu extends FXGLScene {

    protected static final Logger log = Logger.get("FXGL.Menu");

    protected final GameApplication app;

    protected final MenuType type;

    protected MenuEventHandler listener;

    protected final Pane menuRoot = new Pane();
    protected final Pane contentRoot = new Pane();

    protected final MenuContent EMPTY = new MenuContent();

    public FXGLMenu(GameApplication app, MenuType type) {
        this.app = app;
        this.type = type;
        this.listener = (MenuEventHandler) app.getMenuListener();

        getContentRoot().getChildren().addAll(
                createBackground(app.getWidth(), app.getHeight()),
                createTitleView(app.getSettings().getTitle()),
                createVersionView(makeVersionString()),
                menuRoot, contentRoot);

        // we don't data-bind the name because menu subclasses
        // might use some fancy UI without Text / Label
        listener.profileNameProperty().addListener((o, oldName, newName) -> {
            if (!oldName.isEmpty()) {
                // remove last node which *should* be profile view
                getContentRoot().getChildren().remove(getContentRoot().getChildren().size() - 1);
            }

            getContentRoot().getChildren().add(createProfileView("Profile: " + newName));
        });
    }

    /**
     * Switches current active menu body to given.
     *
     * @param menuBox parent node containing menu body
     */
    protected void switchMenuTo(Node menuBox) {
        // no default implementation
    }

    /**
     * Switches current active content to given.
     *
     * @param content menu content
     */
    protected void switchMenuContentTo(Node content) {
        // no default implementation
    }

    protected abstract Button createActionButton(String name, Runnable action);

    protected Button createContentButton(String name, Supplier<MenuContent> contentSupplier) {
        return createActionButton(name, () -> switchMenuContentTo(contentSupplier.get()));
    }

    /**
     * @return full version string
     */
    private String makeVersionString() {
        return "v" + app.getSettings().getVersion()
                + (app.getSettings().getApplicationMode() == ApplicationMode.RELEASE
                ? "" : "-" + app.getSettings().getApplicationMode());
    }

    /**
     * Create menu background.
     *
     * @param width width of the app
     * @param height height of the app
     * @return menu background UI object
     */
    protected abstract Node createBackground(double width, double height);

    /**
     * Create view for the app title.
     *
     * @param title app title
     * @return UI object
     */
    protected abstract Node createTitleView(String title);

    /**
     * Create view for version string.
     *
     * @param version version string
     * @return UI object
     */
    protected abstract Node createVersionView(String version);

    /**
     * Create view for profile name.
     *
     * @param profileName profile user name
     * @return UI object
     */
    protected abstract Node createProfileView(String profileName);

    /**
     * @return menu content containing list of save files and loadTask/delete buttons
     */
    protected final MenuContent createContentLoad() {
        log.debug("createContentLoad()");

        ListView<SaveFile> list = FXGL.getUIFactory().newListView();

        list.setItems(listener.getSaveLoadManager().saveFiles());
        list.prefHeightProperty().bind(Bindings.size(list.getItems()).multiply(36));

        // this runs async
        listener.getSaveLoadManager().querySaveFiles();

        Button btnLoad = FXGL.getUIFactory().newButton("LOAD");
        btnLoad.textProperty().bind(localizedStringProperty("menu.load"));
        btnLoad.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());

        btnLoad.setOnAction(e -> {
            SaveFile saveFile = list.getSelectionModel().getSelectedItem();

            fireLoad(saveFile);
        });

        Button btnDelete = FXGL.getUIFactory().newButton("DELETE");
        btnDelete.textProperty().bind(localizedStringProperty("menu.delete"));
        btnDelete.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());

        btnDelete.setOnAction(e -> {
            SaveFile saveFile = list.getSelectionModel().getSelectedItem();

            fireDelete(saveFile);
        });

        HBox hbox = new HBox(50, btnLoad, btnDelete);
        hbox.setAlignment(Pos.CENTER);

        return new MenuContent(list, hbox);
    }

    /**
     * @return menu content with difficulty and playtime
     */
    protected final MenuContent createContentGameplay() {
        log.debug("createContentGameplay()");

        Spinner<GameDifficulty> difficultySpinner =
                new FXGLSpinner<>(FXCollections.observableArrayList(GameDifficulty.values()));
        difficultySpinner.increment();

        app.getGameState().gameDifficultyProperty().bind(difficultySpinner.valueProperty());

        return new MenuContent(
                new HBox(25, FXGL.getUIFactory().newText(localizedStringProperty("menu.difficulty").concat(":")), difficultySpinner),
                FXGL.getUIFactory().newText("PLAYTIME: "
                        + app.getGameplay().getStats().getPlaytimeHours() + "H "
                        + app.getGameplay().getStats().getPlaytimeMinutes() + "M "
                        + app.getGameplay().getStats().getPlaytimeSeconds() + "S")
                );

    }

    /**
     * @return menu content containing input mappings (action -> key/mouse)
     */
    protected final MenuContent createContentControls() {
        log.debug("createContentControls()");

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 10, 10, 10));
        grid.getColumnConstraints().add(new ColumnConstraints(100, 100, 100, Priority.ALWAYS, HPos.LEFT, true));
        grid.getRowConstraints().add(new RowConstraints(40, 40, 40, Priority.ALWAYS, VPos.CENTER, true));

        // row 0
        grid.setUserData(0);

        app.getInput().getBindings().forEach((action, trigger) -> addNewInputBinding(action, trigger, grid));

        // TODO: use specific style class, i.e. FXGLScrollPane
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setVbarPolicy(ScrollBarPolicy.ALWAYS);
        scroll.setMaxHeight(app.getHeight() / 2.5);

        HBox hbox = new HBox(scroll);
        hbox.setAlignment(Pos.CENTER);

        return new MenuContent(hbox);
    }

    private void addNewInputBinding(UserAction action, Trigger trigger, GridPane grid) {
        Text actionName = FXGL.getUIFactory().newText(action.getName(), Color.WHITE, 18.0);

        TriggerView triggerView = new TriggerView(trigger);
        triggerView.triggerProperty().bind(app.getInput().triggerProperty(action));

        triggerView.setOnMouseClicked(event -> {
            Rectangle rect = new Rectangle(250, 100);
            rect.setStroke(Color.AZURE);

            Text text = FXGL.getUIFactory().newText("PRESS ANY KEY", 24);

            Stage stage = new Stage(StageStyle.TRANSPARENT);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(getRoot().getScene().getWindow());

            Scene scene = new Scene(new StackPane(rect, text));
            scene.setOnKeyPressed(e -> {
                // ignore illegal keys, however they may be part of a different event
                // which is correctly processed further because code will be different
                if (e.getCode() == KeyCode.CONTROL
                        || e.getCode() == KeyCode.SHIFT
                        || e.getCode() == KeyCode.ALT)
                    return;

                boolean rebound = app.getInput().rebind(action, e.getCode(), InputModifier.from(e));

                if (rebound)
                    stage.close();
            });
            scene.setOnMouseClicked(e -> {

                boolean rebound = app.getInput().rebind(action, e.getButton(), InputModifier.from(e));

                if (rebound)
                    stage.close();
            });

            stage.setScene(scene);
            stage.show();
        });

        HBox hBox = new HBox();
        hBox.setPrefWidth(100);
        hBox.setAlignment(Pos.CENTER);
        hBox.getChildren().add(triggerView);

        int controlsRow = (int) grid.getUserData();
        grid.addRow(controlsRow++, actionName, hBox);
        grid.setUserData(controlsRow);
    }

    /**
     * TODO: load default settings from profile
     *
     * @return menu content with video settings
     */
    protected final MenuContent createContentVideo() {
        log.debug("createContentVideo()");

        ChoiceBox<Language> languageBox = FXGL.getUIFactory().newChoiceBox(FXCollections.observableArrayList(Language.values()));
        languageBox.setValue(Language.ENGLISH);

        FXGL.getMenuSettings().languageProperty().bind(languageBox.valueProperty());

        VBox vbox = new VBox();

        if (getSettings().isManualResizeEnabled()) {
            Button btnFixRatio = FXGL.getUIFactory().newButton("Fix Ratio");
            btnFixRatio.setOnAction(e -> {
                listener.fixAspectRatio();
            });

            vbox.getChildren().add(btnFixRatio);
        }

        if (getSettings().isFullScreenAllowed()) {
            CheckBox cbFullScreen = FXGL.getUIFactory().newCheckBox();
            cbFullScreen.setSelected(false);
            cbFullScreen.selectedProperty().bindBidirectional(FXGL.getMenuSettings().fullScreenProperty());

            vbox.getChildren().add(new HBox(25, FXGL.getUIFactory().newText("Fullscreen: "), cbFullScreen));
        }

        return new MenuContent(
                new HBox(25, FXGL.getUIFactory().newText(localizedStringProperty("menu.language").concat(":")), languageBox),
                vbox
        );
    }

    /**
     * @return menu content containing music and sound volume sliders
     */
    protected final MenuContent createContentAudio() {
        log.debug("createContentAudio()");

        Slider sliderMusic = new Slider(0, 1, 1);
        sliderMusic.valueProperty().bindBidirectional(app.getAudioPlayer().globalMusicVolumeProperty());

        Text textMusic = FXGL.getUIFactory().newText("Music Volume: ");
        Text percentMusic = FXGL.getUIFactory().newText("");
        percentMusic.textProperty().bind(sliderMusic.valueProperty().multiply(100).asString("%.0f"));

        Slider sliderSound = new Slider(0, 1, 1);
        sliderSound.valueProperty().bindBidirectional(app.getAudioPlayer().globalSoundVolumeProperty());

        Text textSound = FXGL.getUIFactory().newText("Sound Volume: ");
        Text percentSound = FXGL.getUIFactory().newText("");
        percentSound.textProperty().bind(sliderSound.valueProperty().multiply(100).asString("%.0f"));

        HBox hboxMusic = new HBox(15, textMusic, sliderMusic, percentMusic);
        HBox hboxSound = new HBox(15, textSound, sliderSound, percentSound);

        hboxMusic.setAlignment(Pos.CENTER_RIGHT);
        hboxSound.setAlignment(Pos.CENTER_RIGHT);

        return new MenuContent(hboxMusic, hboxSound);
    }

    /**
     * @return menu content containing a list of credits
     */
    protected final MenuContent createContentCredits() {
        log.debug("createContentCredits()");

        ScrollPane pane = new ScrollPane();
        pane.setPrefWidth(app.getWidth() * 3 / 5);
        pane.setPrefHeight(app.getHeight() / 2);
        pane.setStyle("-fx-background:black;");

        VBox vbox = new VBox();
        vbox.setAlignment(Pos.CENTER);
        vbox.setPrefWidth(pane.getPrefWidth() - 15);

        FXGL.getSettings()
                .getCredits()
                .getList()
                .stream()
                .map(FXGL.getUIFactory()::newText)
                .forEach(vbox.getChildren()::add);

        pane.setContent(vbox);

        return new MenuContent(pane);
    }

    /**
     * @return menu content containing feedback options
     */
    protected final MenuContent createContentFeedback() {
        log.debug("createContentFeedback()");

        // url is a string key defined in system.properties
        Consumer<String> openBrowser = url -> {
            FXGL.getNet()
                    .openBrowserTask(FXGL.getString(url))
                    .onFailure(error -> log.warning("Error opening browser: " + error))
                    .execute();
        };

        Button btnGoogle = new Button("Google Forms");
        btnGoogle.setOnAction(e -> openBrowser.accept("url.googleforms"));

        Button btnSurveyMonkey = new Button("Survey Monkey");
        btnSurveyMonkey.setOnAction(e -> openBrowser.accept("url.surveymonkey"));

        VBox vbox = new VBox(15,
                FXGL.getUIFactory().newText("Choose your feedback method", Color.WHEAT, 18),
                btnGoogle,
                btnSurveyMonkey);
        vbox.setAlignment(Pos.CENTER);

        return new MenuContent(vbox);
    }

    /**
     * @return menu content containing a list of achievements
     */
    protected final MenuContent createContentAchievements() {
        log.debug("createContentAchievements()");

        MenuContent content = new MenuContent();

        for (Achievement a : app.getGameplay().getAchievementManager().getAchievements()) {
            CheckBox checkBox = new CheckBox();
            checkBox.setDisable(true);
            checkBox.selectedProperty().bind(a.achievedProperty());

            Text text = FXGL.getUIFactory().newText(a.getName());
            Tooltip.install(text, new Tooltip(a.getDescription()));

            HBox box = new HBox(25, text, checkBox);
            box.setAlignment(Pos.CENTER_RIGHT);

            content.getChildren().add(box);
        }

        return content;
    }

    /**
     * A generic vertical box container for menu content
     * where each element is followed by a separator.
     */
    protected static class MenuContent extends VBox {
        public MenuContent(Node... items) {

            if (items.length > 0) {
                int maxW = Arrays.stream(items)
                        .mapToInt(n -> (int) n.getLayoutBounds().getWidth())
                        .max()
                        .orElse(0);

                getChildren().add(createSeparator(maxW));

                for (Node item : items) {
                    getChildren().addAll(item, createSeparator(maxW));
                }
            }

            sceneProperty().addListener((o, oldScene, newScene) -> {
                if (newScene != null) {
                    onOpen();
                } else {
                    onClose();
                }
            });
        }

        private Line createSeparator(int width) {
            if (width < 5) {
                width = 200;
            }

            Line sep = new Line();
            sep.setEndX(width);
            sep.setStroke(Color.DARKGREY);
            return sep;
        }

        private Runnable onOpen = null;
        private Runnable onClose = null;

        /**
         * Set on open handler.
         *
         * @param onOpenAction method to be called when content opens
         */
        public void setOnOpen(Runnable onOpenAction) {
            this.onOpen = onOpenAction;
        }

        /**
         * Set on close handler.
         *
         * @param onCloseAction method to be called when content closes
         */
        public void setOnClose(Runnable onCloseAction) {
            this.onClose = onCloseAction;
        }

        private void onOpen() {
            if (onOpen != null)
                onOpen.run();
        }

        private void onClose() {
            if (onClose != null)
                onClose.run();
        }
    }

    /**
     * Adds a UI node.
     *
     * @param node the node to add
     */
    protected final void addUINode(Node node) {
        getContentRoot().getChildren().add(node);
    }

    /**
     * Can only be fired from main menu.
     * Starts new game.
     */
    protected final void fireNewGame() {
        log.debug("fireNewGame()");

        listener.onNewGame();
    }

    /**
     * Loads the game state from last modified save file.
     */
    protected final void fireContinue() {
        log.debug("fireContinue()");

        listener.onContinue();
    }

    /**
     * Loads the game state from previously saved file.
     *
     * @param fileName name of the saved file
     */
    protected final void fireLoad(SaveFile fileName) {
        log.debug("fireLoad()");

        listener.onLoad(fileName);
    }

    /**
     * Can only be fired from game menu.
     * Saves current state of the game with given file name.
     */
    protected final void fireSave() {
        log.debug("fireSave()");

        listener.onSave();
    }

    /**
     * @param fileName name of the save file
     */
    protected final void fireDelete(SaveFile fileName) {
        log.debug("fireDelete()");

        listener.onDelete(fileName);
    }

    /**
     * Can only be fired from game menu.
     * Will close the menu and unpause the game.
     */
    protected final void fireResume() {
        log.debug("fireResume()");

        listener.onResume();
    }

    /**
     * Can only be fired from main menu.
     * Logs out the user profile.
     */
    protected final void fireLogout() {
        log.debug("fireLogout()");

        switchMenuContentTo(EMPTY);

        listener.onLogout();
    }

    /**
     * Call multiplayer access in main menu.
     * Currently not supported.
     */
    protected final void fireMultiplayer() {
        log.debug("fireMultiplayer()");

        listener.onMultiplayer();
    }

    /**
     * App will clean up the world/the scene and exit.
     */
    protected final void fireExit() {
        log.debug("fireExit()");

        listener.onExit();
    }

    /**
     * App will clean up the world/the scene and enter main menu.
     */
    protected final void fireExitToMainMenu() {
        log.debug("fireExitToMainMenu()");

        listener.onExitToMainMenu();
    }
}
