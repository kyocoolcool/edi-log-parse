package com.example.javafx;

import com.jfoenix.controls.JFXButton;
import javafx.application.HostServices;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.*;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SimpleUiController {


    private final HostServices hostServices;

    @FXML
    public Text statusText;

    @FXML
    public Button fileUploadButton;

    @FXML
    public RadioButton selectAllTime;

    @FXML
    public RadioButton selectAreaTime;

    @FXML
    public ToggleGroup timeCheckButton;

    @FXML
    public TextField fromTime;
    @FXML
    public TextField toTime;

    @FXML
    public Button exportButton;

    private File file;


    public SimpleUiController(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    @FXML
    public void initialize() {
//        this.button.setOnAction(actionEvent -> this.label.setText(aaa.getBbb()));
//        log.info(String.valueOf(this.selectAllTime.isSelected()));
        this.timeCheckButton.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {
            @Override
            public void changed(ObservableValue<? extends Toggle> observable, Toggle oldToggle, Toggle newToggle) {
                if (((RadioButton) newToggle).getText().equals("SelectAllTime")) {
                    fromTime.setDisable(true);
                    toTime.setDisable(true);
                    if (file != null) {
                        exportButton.setDisable(false);
                    }
                } else if (((RadioButton) newToggle).getText().equals("SelectAreaTime")) {
                    fromTime.setDisable(false);
                    toTime.setDisable(false);
                    if (checkFilledFrom()) {
                        exportButton.setDisable(true);
                    }
                }
                log.info(((RadioButton) newToggle).getText());
            }
        });
//        this.fromTime.setOnAction(actionEvent -> {
//            log.info("fromTime");
//            if (checkFilledFrom()) {
//                this.exportButton.setDisable(true);
//            } else {
//                this.exportButton.setDisable(false);
//            }
//        });
//        this.toTime.setOnAction(actionEvent -> {
//            log.info("toTime");
//            if (checkFilledFrom()) {
//                this.exportButton.setDisable(true);
//            } else {
//                this.exportButton.setDisable(false);
//            }
//        });
//        this.fileUploadButton.setOnAction(actionEvent -> {
//            if (this.file != null && this.selectAllTime.isSelected()) {
//                this.exportButton.setDisable(false);
//            } else if (this.file != null && this.selectAreaTime.isSelected() && !this.fromTime.getText().isBlank() && !this.toTime.getText().isBlank()) {
//                this.exportButton.setDisable(false);
//            }
//        });
//        this.selectAreaTime.setOnAction(actionEvent -> log.info(actionEvent.getEventType().getName()));
//        this.button.setOnAction(actionEvent -> this.label.setText(this.hostServices.getDocumentBase()));
    }


    public String extractEdiLog(String record) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");
        LocalDateTime fromLocalDateTIme = null;
        LocalDateTime toLocalDateTIme = null;
        if (this.selectAreaTime.isSelected()) {
            String   fromTime = this.fromTime.getText();
            String  toTime = this.toTime.getText();
             fromLocalDateTIme = LocalDateTime.parse(fromTime,dateTimeFormatter);
             toLocalDateTIme = LocalDateTime.parse(toTime,dateTimeFormatter);
        }
        final Pattern datePattern = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01]) \\d{2}:\\d{2}:\\d{2},\\d{3}");
        final Pattern ediPattern = Pattern.compile("TID.*");
        final Pattern unbPattern = Pattern.compile("UNB\\+");
        final Matcher dateMatcher = datePattern.matcher(record);
        final Matcher ediMatcher = ediPattern.matcher(record);
        final Matcher unbMatcher = unbPattern.matcher(record);
        if (unbMatcher.find()) {
            if (dateMatcher.find()) {
                String date = dateMatcher.group();
                if (this.selectAreaTime.isSelected()) {
                    LocalDateTime recordLocalDateTime = LocalDateTime.parse(date, dateTimeFormatter);
                    boolean withinRange = isWithinRange(recordLocalDateTime, fromLocalDateTIme, toLocalDateTIme);
                    if (withinRange) {
                        return null;
                    }
                }
                if (ediMatcher.find()) {
                    String edi = ediMatcher.group();
                    if (edi != null) {
                        String ediLogRecord = date + "\t" + edi;
                        log.info(ediLogRecord);
                        return ediLogRecord;
                    }
                }
            }
        }
        return null;
    }


    public void exportEDI() throws IOException {
        if (file != null) {
            this.exportButton.setDisable(true);
            statusText.setText("Transfer File...:" + file.getAbsoluteFile());
            List<String> strings = Files.readAllLines(file.toPath());
            log.info("start extract EDI log");
            if (this.selectAreaTime.isSelected()) {
                log.info("fromTime:" + this.fromTime.getText());
                log.info("toTime:" + this.toTime.getText());
            }
            List<String> keyWordRecordList = strings.stream().filter(reocrd -> reocrd.indexOf("Receive") != -1).map(this::extractEdiLog).filter(Objects::nonNull).collect(Collectors.toList());
            log.info("finished extract EDI log");
            log.info("start write record to REDO file");
            writeRecordToRedoFile(keyWordRecordList);
            log.info("finished write record to REDO file");
            statusText.setText("Transfer Finished");
            this.exportButton.setDisable(false);
        }
    }

    public void writeRecordToRedoFile(List recordList) throws IOException {
        Path outPath = Paths.get("REDO");
        Files.write(outPath, recordList, Charset.defaultCharset());
    }

    public void loadFile(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new ExtensionFilter("Log File", "*.log"));
        this.file = fileChooser.showOpenDialog(null);
        if (this.file != null && this.selectAllTime.isSelected()) {
            this.exportButton.setDisable(false);
        } else if (this.file != null && this.selectAreaTime.isSelected() && !this.fromTime.getText().isBlank() && !this.toTime.getText().isBlank()) {
            this.exportButton.setDisable(false);
        }
    }

    public boolean checkFilledFrom() {
        return this.file == null || !this.selectAreaTime.isSelected() || this.fromTime.getText().isBlank() || this.toTime.getText().isBlank();
    }


    public void fromTimeKey(KeyEvent keyEvent) {
        log.info("fromTimeKey");
        this.exportButton.setDisable(checkFilledFrom());
    }

    public void toTimeKey(KeyEvent keyEvent) {
        log.info("toTimeKey");
        this.exportButton.setDisable(checkFilledFrom());
    }

    boolean isWithinRange(LocalDateTime recordLocalDateTime, LocalDateTime fromLocalDateTime, LocalDateTime toLocalDateTime) {
        boolean b = recordLocalDateTime.isBefore(fromLocalDateTime) || recordLocalDateTime.isAfter(toLocalDateTime);
        return b;
    }
}
