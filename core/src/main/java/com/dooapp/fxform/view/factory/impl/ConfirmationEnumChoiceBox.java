package com.dooapp.fxform.view.factory.impl;

import com.dooapp.fxform.AbstractFXForm;
import com.dooapp.fxform.model.Element;
import com.sun.javafx.scene.control.skin.ChoiceBoxSkin;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.stage.WindowEvent;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: Kevin Senechal <kevin@dooapp.com>
 * Date: 27/11/2017
 */
public class ConfirmationEnumChoiceBox<OBJECT extends Enum<OBJECT>> extends FXFormChoiceBoxNode {

    private final static Logger logger = Logger.getLogger(ConfirmationEnumChoiceBox.class.getName());

    private final ChangeListener<OBJECT> tmpChangeListener;
    private ObjectProperty<OBJECT> tmp = new SimpleObjectProperty<>();
    private final ChangeListener<OBJECT> originalChangelistener;

    public ConfirmationEnumChoiceBox(String headerText, String contentText) {
        this(headerText, contentText, (oldValue, newValue) -> true);
    }

    public ConfirmationEnumChoiceBox(String headerText, String contentText, BiFunction<OBJECT, OBJECT, Boolean> askConfirmation) {
        tmpChangeListener = (observable, oldValue, newValue) -> {
            if (!newValue.equals(ConfirmationEnumChoiceBox.super.getProperty().getValue())) {
                ConfirmationEnumChoiceBox.super.getProperty().setValue(newValue);
            }
        };
        tmp.addListener(tmpChangeListener);

        originalChangelistener = new ChangeListener<OBJECT>() {
            @Override
            public void changed(ObservableValue<? extends OBJECT> observable, OBJECT oldValue, OBJECT newValue) {
                // we should remove the listener on the property once it has been triggered.
                ConfirmationEnumChoiceBox.super.getProperty().removeListener(this);
                if (askConfirmation.apply(oldValue, newValue)) {
                    Platform.runLater(() -> checkAndSet(oldValue, newValue));
                } else {
                    tmp.setValue(newValue);
                }
            }

            private void checkAndSet(OBJECT oldValue, OBJECT newValue) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setHeaderText(headerText);
                alert.setContentText(contentText);
                Optional<ButtonType> response = alert.showAndWait();
                if (response.isPresent() && response.get() == ButtonType.OK) {
                    tmp.setValue(newValue);
                } else {
                    Platform.runLater(() -> ConfirmationEnumChoiceBox.super.getProperty().setValue(oldValue));
                }
            }
        };

        // we should add the listener on property when the user display choice box choices.
        // We do it here because if we always listen property and the form is displayed,
        // model update not trigerred by user will popup anyway, which is not the wanted behaviour.
        choiceBox.skinProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                try {
                    Field field = ChoiceBoxSkin.class.getDeclaredField("popup");
                    field.setAccessible(true);
                    ContextMenu popup = (ContextMenu) field.get(newValue);
                    popup.setOnShowing(new EventHandler<WindowEvent>() {
                        @Override
                        public void handle(WindowEvent event) {
                            ConfirmationEnumChoiceBox.super.getProperty().removeListener(originalChangelistener);
                            ConfirmationEnumChoiceBox.super.getProperty().addListener(originalChangelistener);
                        }
                    });
                } catch (Exception e) {
                    logger.log(Level.FINEST, e.getMessage(), e);
                }
            }
        });
    }

    @Override
    public void init(Element element, AbstractFXForm fxForm) {
        Enum[] constants = new Enum[0];
        try {
            constants = (Enum[]) element.getWrappedType().getEnumConstants();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not retrieve enum constants from element " + element, e);
        }
        this.choiceBox.setItems(FXCollections.observableList(Arrays.asList(constants)));
        this.choiceBox.getSelectionModel().select(element.getValue());
    }

    @Override
    public boolean isEditable() {
        return true;
    }

    @Override
    public void dispose() {
        tmp.removeListener(tmpChangeListener);
        super.getProperty().removeListener(originalChangelistener);
        super.dispose();
    }

    @Override
    public Property getProperty() {
        return tmp;
    }

}
