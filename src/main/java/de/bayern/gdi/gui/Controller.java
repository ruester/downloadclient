/*
 * DownloadClient Geodateninfrastruktur Bayern
 *
 * (c) 2016 GSt. GDI-BY (gdi.bayern.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.bayern.gdi.gui;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.stage.WindowEvent;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import de.bayern.gdi.processor.Processor;
import de.bayern.gdi.processor.ProcessorEvent;
import de.bayern.gdi.processor.ProcessorListener;
import de.bayern.gdi.services.Atom;
import de.bayern.gdi.services.ServiceType;
import de.bayern.gdi.services.WFSOne;
import de.bayern.gdi.services.WFSTwo;
import de.bayern.gdi.services.WebService;
import de.bayern.gdi.utils.I18n;
import de.bayern.gdi.utils.ServiceChecker;

/**
 * @author Jochen Saalfeld (jochen@intevation.de)
 */
public class Controller {

    private static final int MAP_WIDTH = 300;
    private static final int MAP_HEIGHT = 250;

    // DataBean
    private DataBean dataBean;

    @FXML private MenuItem menuCloseApp;
    @FXML private MenuBar mainMenu;
    @FXML private ListView serviceList;
    @FXML private TextField searchField;
    @FXML private TextField serviceURL;
    @FXML private CheckBox serviceAuthenticationCbx;
    @FXML private TextField serviceUser;
    @FXML private TextField servicePW;
    @FXML private Label statusBarText;
    @FXML private ComboBox serviceTypeChooser;
    @FXML private VBox simpleWFSContainer;
    @FXML private VBox basicWFSContainer;
    @FXML private VBox atomContainer;
    @FXML private Group mapNodeWFS;
    @FXML private Group mapNodeAtom;

    /**
     * Handler to close the application.
     *
     * @param event The event.
     */
    @FXML protected void handleCloseApp(ActionEvent event) {
        Stage stage = (Stage) mainMenu.getScene().getWindow();
        stage.fireEvent(new WindowEvent(
            stage,
            WindowEvent.WINDOW_CLOSE_REQUEST
        ));
    }

    /**
     * Handle the service selection.
     *
     * @param event The mouse click event.
     */
    @FXML protected void handleServiceSelectButton(MouseEvent event) {
        if (event.getButton().equals(MouseButton.PRIMARY)) {
            chooseService();
        }
    }

    /**
     * Handle search and filter the service list.
     *
     * @param event the event
     */
    @FXML protected void handleSearch(KeyEvent event) {
        String currentText = this.searchField.getText();
        if ("".equals(currentText) || currentText == null) {
            this.dataBean.reset();
            this.serviceList.setItems(this.dataBean.getServicesAsList());
        }
        String searchValue = currentText.toUpperCase();
        ObservableList<String> subentries
                = FXCollections.observableArrayList();
        if (currentText.length() > 2) {
            Map<String, String> catalog = dataBean.getCatalogService()
                    .getServicesByFilter(currentText);
            for (Map.Entry<String, String> entry: catalog.entrySet()) {
                dataBean.addCatalogServiceToList(
                    entry.getKey(), entry.getValue());
            }
        }
        ObservableList<String> all = this.dataBean.getServicesAsList();
        for (String entry : all) {
            boolean match = true;
            if (!entry.toUpperCase().contains(searchValue)) {
                match = false;
            }
            if (match) {
                subentries.add(entry);
            }
        }
        this.serviceList.setItems(subentries);
    }

    /**
     * Handle the service selection.
     *
     * @param event The mouse click event.
     */
    @FXML protected void handleServiceSelect(MouseEvent event) {
        if (event.getButton().equals(MouseButton.PRIMARY)
            && event.getClickCount() > 1
        ) {
            chooseService();
        } else if (event.getButton().equals(MouseButton.PRIMARY)
            && event.getClickCount() == 1
        ) {
            if (this.serviceList.getSelectionModel().getSelectedItems().get(0)
                    != null
            ) {
                String serviceName =
                    this.serviceList.getSelectionModel().
                        getSelectedItems().get(0).toString();
                String url = dataBean.getServiceURL(serviceName);
                this.serviceURL.setText(url);
            }
        }
    }

    /**
     * Handle the service type selection.
     *
     * @param event The event
     */
    @FXML protected void handleServiceTypeSelect(ActionEvent event) {
        String item =
            this.serviceTypeChooser.
                getSelectionModel().getSelectedItem().toString();
        System.out.println("Selected: " + item);

    }

    /**
     * Handle the dataformat selection.
     *
     * @param event The event
     */
    @FXML protected void handleDataformatSelect(ActionEvent event) {
        System.out.println("Format select...");
    }

    /**
     * Handle the reference system selection.
     *
     * @param event The event
     */
    @FXML protected void handleReferenceSystemSelect(ActionEvent event) {
        System.out.println("Refsys select...");
    }

    /**
     * Use selection to request the service data and fill th UI.
     */
    private void chooseService() {
        Task task = new Task() {
            @Override
            protected Integer call() throws Exception {
                serviceURL.getScene().setCursor(Cursor.WAIT);
                String url = null;
                String username = null;
                String password = null;
                if (serviceList.getSelectionModel().getSelectedItems().get(0)
                        != null) {
                    String serviceName =
                            serviceList.
                                getSelectionModel().
                                    getSelectedItems().get(0).toString();
                    url = dataBean.getServiceURL(serviceName);
                } else {
                    url = serviceURL.getText();
                }
                if (serviceAuthenticationCbx.isSelected()) {
                    username = serviceUser.getText();
                    dataBean.setUsername(username);
                    password = servicePW.getText();
                    dataBean.setPassword(password);
                }
                if (url != null) {
                    //view.setStatusBarText("Check for Servicetype");
                    ServiceType st =
                            ServiceChecker.checkService(url,
                                    dataBean.getBase64EncAuth());
                    WebService ws = null;
                    //Check for null, since switch breaks on a null value
                    if (st == null) {
                        log.log(Level.WARNING, "Could not determine "
                                + "Service Type" , st);
                        Platform.runLater(() -> {
                            statusBarText.setText(
                                I18n.getMsg("status.no-service-type"));
                        });
                    } else {
                        switch (st) {
                            case Atom:
                                Platform.runLater(() -> {
                                    statusBarText.setText(
                                        I18n.getMsg("status.type.atom"));
                                });
                                ws = new Atom(url);
                                break;
                            case WFSOne:
                                Platform.runLater(() -> {
                                    statusBarText.setText(
                                        I18n.getMsg("status.type.wfsone"));
                                });
                                ws = new WFSOne(url, dataBean
                                        .getUserName(), dataBean
                                        .getPassword());
                                break;
                            case WFSTwo:
                                Platform.runLater(() -> {
                                    statusBarText.setText(
                                        I18n.getMsg("status.type.wfstwo"));
                                });
                                ws = new WFSTwo(url, dataBean
                                        .getUserName(), dataBean
                                        .getPassword());
                                break;
                            default:
                                log.log(Level.WARNING,
                                    "Could not determine URL" , st);
                                Platform.runLater(() -> {
                                    statusBarText.setText(
                                            I18n.getMsg("status.no-url"));
                                });
                                break;
                        }
                    }
                    dataBean.setWebService(ws);
                    Platform.runLater(() -> {
                        setServiceTypes();
                        serviceTypeChooser.
                                getSelectionModel().select(0);
                        /*ChooseTypeEventHandler chooseType
                                = new ChooseTypeEventHandler();
                        chooseType.handle(e);*/
                        statusBarText.setText(I18n.getMsg("status.ready"));
                    });
                } else {
                    Platform.runLater(() -> {
                        statusBarText.setText(I18n.getMsg("status.no-url"));
                    });
                }
                serviceURL.getScene().setCursor(Cursor.DEFAULT);
                return 0;
            }
        };
        Thread th = new Thread(task);
        statusBarText.setText(I18n.getMsg("status.calling-service"));
        th.setDaemon(true);
        th.start();
    }

    /**
     * Sets the Service Types.
     */
    public void setServiceTypes() {
        if (dataBean.isWebServiceSet()) {
            switch (dataBean.getWebService().getServiceType()) {
                case WFSOne:
                    dataBean.setServiceTypes(
                            dataBean.getWebService().getTypes());
                    break;
                case WFSTwo:
                    WFSTwo wfstwo = (WFSTwo) dataBean.getWebService();
                    ArrayList<String> wfstwoServices = new ArrayList<>();
                    ArrayList<String> storedQuieres = dataBean
                        .getWebService().getStoredQueries();
                    for (String str: storedQuieres) {
                        str = wfstwo.getSimplePrefix() + " " + str;
                        wfstwoServices.add(str);
                    }
                    ArrayList<String> types = dataBean
                            .getWebService().getTypes();
                    for (String str: types) {
                        str = wfstwo.getBasicPrefix() + " " + str;
                        wfstwoServices.add(str);
                    }
                    dataBean.setServiceTypes(wfstwoServices);
                    break;
                case Atom:
                default:
            }
            ObservableList<String> options =
                FXCollections.observableArrayList(dataBean.getServiceTypes());
            serviceTypeChooser.getItems().retainAll();
            serviceTypeChooser.setItems(options);
        }
    }

    /**
     * Set the DataBean and fill the UI with initial data objects.
     *
     * @param dataBean  The DataBean object.
     */
    public void setDataBean(DataBean dataBean) {
        this.dataBean = dataBean;
        this.serviceList.setItems(this.dataBean.getServicesAsList());
        URL url = null;
        try {
            url = new URL(this.dataBean.getWmsUrl());
        } catch (MalformedURLException e) {
        }
        WMSMapSwing mapWFS = new WMSMapSwing(url, MAP_WIDTH, MAP_HEIGHT);
        WMSMapSwing mapAtom = new WMSMapSwing(url, MAP_WIDTH, MAP_HEIGHT);

        this.mapNodeWFS.getChildren().add(mapWFS);
        this.mapNodeAtom.getChildren().add(mapAtom);

        this.simpleWFSContainer.setVisible(false);
        this.basicWFSContainer.setVisible(true);
        this.atomContainer.setVisible(false);
    }

    // view
    //private view view;

    private static final Logger log
            = Logger.getLogger(Controller.class.getName());
    /**
     * Creates the Controller.
     * @param dataBean the model
     */
    public Controller() {
/*
        //this.dataBean = dataBean;
        //this.view = new View();
        //this.view.setServiceListEntries(this.dataBean.getServicesAsList());
        //this.view.setCatalogueServiceNameLabelText(
        //        I18n.getMsg("gui.catalogue") + ": "
        //        + this.dataBean.getCatalogService().getProviderName());

        // Register Event Handler
        /*view.getQuitMenuItem().
                setOnAction(new QuitMenuItemEventHandler());
        view.getResetMenuItem().
                setOnAction(new ResetMenuItemEventHandler());
        view.getServiceChooseButton().
                setOnAction(new ServiceChooseButtonEventHandler());
        view.getTypeComboBox().
                setOnAction(new ChooseTypeEventHandler());
        view.getAttributesFilledButton().
                setOnAction(new AttributesFilledEventHandler());
        //FIXME - Not only on Click, but everytime
        view.getServiceList().
                setOnMouseClicked(new MouseClickedOnServiceList());
        view.getDownloadButton().
                setOnAction(new DownloadButtonEventHandler());
        view.getSaveMenuItem().
                setOnAction(new SaveMenuItemEventHandler());
        //TODO - Implement Loading Function
        /*
        view.getLoadMenuItem().
                setOnAction(new LoadMenuItemEventHandler());

        // Register Listener
        view.getServiceSearch().textProperty().
                addListener(new SearchServiceListChangeListener());
*/
        Processor.getInstance().addListener(new DownloadListener());

        // stage overrides
//        this.dataBean.getPrimaryStage().
//                setOnCloseRequest(new ConfirmCloseEventHandler());

    }

    /**
     * shows the view.
     *
    public void show() {
    //    view.show(dataBean.getPrimaryStage());
    }

    /**
     * sets the Service Types.
     *
    public void setServiceTypes() {
        if (dataBean.isWebServiceSet()) {
            switch (dataBean.getWebService().getServiceType()) {
                case WFSOne:
                    dataBean.setServiceTypes(
                            dataBean.getWebService().getTypes());
                    break;
                case WFSTwo:
                    WFSTwo wfstwo = (WFSTwo) dataBean.getWebService();
                    ArrayList<String> wfstwoServices = new ArrayList<>();
                    ArrayList<String> storedQuieres = dataBean
                        .getWebService().getStoredQueries();
                    for (String str: storedQuieres) {
                        str = wfstwo.getSimplePrefix() + " " + str;
                        wfstwoServices.add(str);
                    }
                    ArrayList<String> types = dataBean
                            .getWebService().getTypes();
                    for (String str: types) {
                        str = wfstwo.getBasicPrefix() + " " + str;
                        wfstwoServices.add(str);
                    }
                    dataBean.setServiceTypes(wfstwoServices);
                    break;
                case Atom:
                default:
            }
//            view.setTypes(dataBean.getServiceTypes());
        }
    }

    /**
     * sets the Service Types Attributes.
     * @param map the Map of Attributes
     *
    public void setServiceAttributes(Map<String, String> map) {
        if (dataBean.isWebServiceSet()) {
  //          view.setAttributes(map);
            setWMSMap(this.dataBean.getWmsUrl(), this.dataBean.getWmsName());
        }
    }

    private void setWMSMap(String wmsUrl, String wmsName) {
  //      view.setWMSMap(wmsUrl, wmsName);
    }

    //+++++++++++++++++++++++++++++++++++++++++++++
    // Listener
    //+++++++++++++++++++++++++++++++++++++++++++++

    /**
     * listener for changes in search field, so the list can be searched.
     *
    @FXML
    protected void searchServiceList(String oldVal, String newVal) {
        if (oldVal != null && (newVal.length() < oldVal.length())) {
//            view.getServiceList().setItems(view.getServiceListEntries());
        }
        String value = newVal.toUpperCase();
        ObservableList<String> subentries
                = FXCollections.observableArrayList();
        Map<String, String> catalog = dataBean.getCatalogService()
                .getServicesByFilter(newVal);
        for (Map.Entry<String, String> entry: catalog.entrySet()) {
//            view.addServiceToList(entry.getKey());
            dataBean.addServiceToList(entry.getKey(), entry.getValue());
        }
//        for (Object entry : view.getServiceList().getItems()) {
            boolean match = true;
            String entryText = (String) entry;
            if (!entryText.toUpperCase().contains(value)) {
                match = false;
                break;
            }
            if (match) {
                subentries.add(entryText);
            }
        }
//        view.getServiceList().setItems(subentries);
    }

    //+++++++++++++++++++++++++++++++++++++++++++++
    // Events
    //+++++++++++++++++++++++++++++++++++++++++++++

    /**
     * Event Handler for choosing a type.
     *
    private class ChooseTypeEventHandler
        implements EventHandler<ActionEvent> {
        @Override
        public void handle(ActionEvent e) {
            Map<String, String> map = new HashMap<String, String>();
//            if (view.getTypeComboBox().getSelectionModel().getSelectedItem()
//                    != null) {
//                String choosenType =
//                        view.getTypeComboBox().getSelectionModel()
//                        .getSelectedItem()
//                        .toString();
                ArrayList <AttributeType> attributes = null;
                switch (dataBean.getWebService().getServiceType()) {
                    case WFSOne:
                        map = dataBean.getWebService()
                                        .getAttributes(choosenType);
                        break;
                    case WFSTwo:
                        if (choosenType.startsWith(WFSTwo.getSimplePrefix())) {
                            choosenType = choosenType.substring(WFSTwo
                                    .getSimplePrefix().length() + 1);
                            map.putAll(dataBean.getWebService()
                                    .getParameters(choosenType));
                        } else {
                            choosenType = choosenType.substring(WFSTwo
                                    .getBasicPrefix().length() + 1);
                            map.putAll(dataBean.getWebService()
                                    .getAttributes(choosenType));
                        }
                        break;
                    case Atom:
                    default:
                }
                dataBean.setAttributes(map);
                setServiceAttributes(dataBean.getAttributes());
            }
        }
    }

    /**
     * Class for handling stuff if Attributes are filled.
     *
    private class AttributesFilledEventHandler
            implements EventHandler<ActionEvent> {
        @Override
        public void handle(ActionEvent e) {
            Map map = dataBean.getAttributes();
            for (Node n:view.getAttributeGridPane().getChildren()) {
                System.out.println(n.toString());
            }
        }
    }

    /**
     * Event handler for clicking "Save".
     *
    private class LoadMenuItemEventHandler
            implements EventHandler<ActionEvent> {
        @Override
        public void handle(ActionEvent e) {
            /* TODO: Implent this function in a way so the configuration File
             * fills the Frontend with the Informtaion. So all fields in the
             * frontend should be filled with the Information from the config
             * file, so you can check and verify your settings before clicking
             * "download"
             *
            FileChooser configFileChooser = new FileChooser();
            configFileChooser.setTitle(I18n.getMsg("gui.load-conf"));

            File configFile = configFileChooser.showOpenDialog(
                    dataBean.getPrimaryStage());
            if (configFile == null) {
                return;
            }
            try {
                DownloadStep ds = DownloadStep.read(configFile);
                FileChooser downloadFileChooser = new FileChooser();
                downloadFileChooser.setTitle(I18n.getMsg("gui.save-conf"));
                downloadFileChooser.setInitialDirectory(new File(ds.getPath()));
                File downloadFile = downloadFileChooser.showSaveDialog(
                        dataBean.getPrimaryStage());
                if (downloadFile == null) {
                    return;
                }
                ds.setPath(downloadFile.toString());
                JobList jl = DownloadStepConverter.convert(ds);
                Processor p = Processor.getInstance();
                p.addJob(jl);
            } catch (IOException | ConverterException ex) {
                log.log(Level.WARNING, ex.getMessage() , ex);
            }
        }
    }

    /**
     * Event handler for clicking "Save".
     *
    private class SaveMenuItemEventHandler
            implements EventHandler<ActionEvent> {
        @Override
        public void handle(ActionEvent e) {
            FileChooser downloadFileChooser = new FileChooser();
            downloadFileChooser.setTitle(I18n.getMsg("gui.save-file"));
            File downloadFile = downloadFileChooser.showSaveDialog(
                    dataBean.getPrimaryStage());
            if (downloadFile == null) {
                return;
            }
            FileChooser configFileChooser = new FileChooser();
            configFileChooser.setTitle(I18n.getMsg("gui.save-conf"));
            File configFile = configFileChooser.showSaveDialog(
                    dataBean.getPrimaryStage());
            if (configFile == null) {
                return;
            }
            String savePath = downloadFile.getPath();
            DownloadStepFactory dsf = DownloadStepFactory.getInstance();
            DownloadStep ds = dsf.getStep(view, dataBean, savePath);
            try {
                ds.write(configFile);

            } catch (IOException ex) {
                log.log(Level.WARNING, ex.getMessage() , ex);
            }
        }
    }

    /**
     * Event Handler for the Quit Programm Menu Entry.
     *
    private class QuitMenuItemEventHandler
            implements EventHandler<ActionEvent> {
        @Override
        public void handle(ActionEvent e) {
            dataBean.getPrimaryStage().fireEvent(
                            new WindowEvent(
                                    dataBean.getPrimaryStage(),
                                    WindowEvent.WINDOW_CLOSE_REQUEST
                            )
            );
        }
    }

    /**
     * Event Handler for resetting the programm.
     *
    private class ResetMenuItemEventHandler
            implements EventHandler<ActionEvent> {
        @Override
        public void handle(ActionEvent e) {
            view.reset();
        }
    }

    /**
     * Event Handler for Downloading.
     *
    private class DownloadButtonEventHandler
            implements EventHandler<ActionEvent> {
        @Override
        public void handle(ActionEvent e) {
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle(I18n.getMsg("gui.save-dir"));
            //fileChooser.getExtensionFilters().addAll();
            File selectedDir = dirChooser.showDialog(
                    dataBean.getPrimaryStage());
            if (selectedDir == null) {
                return;
            }
            Task task = new Task() {
                @Override
                protected Integer call() throws Exception {
                    String savePath = selectedDir.getPath();
                    DownloadStepFactory dsf = DownloadStepFactory.getInstance();
                    DownloadStep ds = dsf.getStep(view, dataBean, savePath);
                    JobList jl = DownloadStepConverter.convert(ds);
                    Processor p = Processor.getInstance();
                    p.addJob(jl);
                    return 0;
                }
            };
            Thread th = new Thread(task);
            th.setDaemon(true);
            th.start();
        }
    }

    /**
     * Event Handler for the choose Service Button.
     *
    private class ServiceChooseButtonEventHandler implements
            EventHandler<ActionEvent> {

        public void handle(MouseEvent e) {
            ActionEvent ev = new ActionEvent();
            handle(ev);
        }

        @Override
        public void handle(ActionEvent e) {
            Task task = new Task() {
                @Override
                protected Integer call() throws Exception {
                    view.getScene().setCursor(Cursor.WAIT);
                    String serviceURL = null;
                    String username = null;
                    String password = null;
                    if (view.getServiceUseAuthenticationCBX().
                            isSelected()) {
                        username =
                                view.getServiceUser().
                                        textProperty().getValue();
                        dataBean.setUsername(username);
                        password =
                                view.getServicePW().
                                        textProperty().getValue();
                        dataBean.setPassword(password);
                    }
                    if (view.getServiceList().
                            getSelectionModel().getSelectedItems().get(0)
                            != null) {
                        String serviceName =
                                view.getServiceList().
                                        getSelectionModel().
                                        getSelectedItems().get(0);
                        serviceURL = dataBean.getServiceURL(serviceName);
                    } else {
                        serviceURL =
                                view.getServiceURLfield().
                                        textProperty().getValue();
                    }
                    if (view.getServiceUseAuthenticationCBX().
                            isSelected()) {
                        username = view.getServiceUser().
                                        textProperty().getValue();
                        dataBean.setUsername(username);
                        password = view.getServicePW().
                                        textProperty().getValue();
                        dataBean.setPassword(password);
                    }
                    if (serviceURL != null) {
                        //view.setStatusBarText("Check for Servicetype");
                        ServiceType st =
                                ServiceChecker.checkService(serviceURL,
                                        dataBean.getBase64EncAuth());
                        WebService ws = null;
                        //Check for null, since switch breaks on a null value
                        if (st == null) {
                            log.log(Level.WARNING, "Could not determine "
                                    + "Service Type" , st);
                            Platform.runLater(() -> {
                                view.setStatusBarText(
                                    I18n.getMsg("status.no-service-type"));
                            });
                        } else {
                            switch (st) {
                                case Atom:
                                    Platform.runLater(() -> {
                                        view.setStatusBarText(
                                            I18n.getMsg("status.type.atom"));
                                    });
                                    ws = new Atom(serviceURL);
                                    break;
                                case WFSOne:
                                    Platform.runLater(() -> {
                                        view.setStatusBarText(
                                            I18n.getMsg("status.type.wfsone"));
                                    });
                                    ws = new WFSOne(serviceURL, dataBean
                                            .getUserName(), dataBean
                                            .getPassword());
                                    break;
                                case WFSTwo:
                                    Platform.runLater(() -> {
                                        view.setStatusBarText(
                                            I18n.getMsg("status.type.wfstwo"));
                                    });
                                    ws = new WFSTwo(serviceURL, dataBean
                                            .getUserName(), dataBean
                                            .getPassword());
                                    break;
                                default:
                                    log.log(Level.WARNING,
                                        "Could not determine URL" , st);
                                    Platform.runLater(() -> {
                                        view.setStatusBarText(
                                                I18n.getMsg("status.no-url"));
                                    });
                                    break;
                            }
                        }
                        dataBean.setWebService(ws);
                        Platform.runLater(() -> {
                            setServiceTypes();
                            view.getTypeComboBox().
                                    getSelectionModel().select(0);
                            ChooseTypeEventHandler chooseType
                                    = new ChooseTypeEventHandler();
                            chooseType.handle(e);
                            view.setStatusBarText(I18n.getMsg("status.ready"));
                        });
                    } else {
                        Platform.runLater(() -> {
                            view.setStatusBarText(I18n.getMsg("status.no-url"));
                        });
                    }
                    view.getScene().setCursor(Cursor.DEFAULT);
                    return 0;
                }
            };
            Thread th = new Thread(task);
            view.setStatusBarText(I18n.getMsg("status.calling-service"));
            th.setDaemon(true);
            th.start();
        }
    }

    /**
     * Event Handler for closing the Application.
     *
    private class ConfirmCloseEventHandler implements
            EventHandler<WindowEvent> {
        @Override
        public void handle(WindowEvent e) {
            Alert closeConfirmation = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    I18n.getMsg("gui.want-to-quit")
            );
            Button exitButton
                    = (Button) closeConfirmation.getDialogPane().lookupButton(
                        ButtonType.OK
                    );
            exitButton.setText(I18n.getMsg("gui.exit"));
            closeConfirmation.setHeaderText(I18n.getMsg("gui.confirm-exit"));
            closeConfirmation.initModality(Modality.APPLICATION_MODAL);
            closeConfirmation.initOwner(dataBean.getPrimaryStage());

            Optional<ButtonType> closeResponse =
                    closeConfirmation.showAndWait();
            if (!ButtonType.OK.equals(closeResponse.get())) {
                e.consume();
            }
        }
    }

    /**
     *  Eventhandler for mouse events on map.
     *
    private class MouseClickedOnServiceList
            implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent e) {
            if (e.getButton().equals(MouseButton.PRIMARY)) {
                if (e.getClickCount() > 1) {
                    ServiceChooseButtonEventHandler se =
                            new ServiceChooseButtonEventHandler();
                    se.handle(e);
                }
                if (e.getClickCount() == 1) {
                    if (view.getServiceList().
                            getSelectionModel().getSelectedItems().get(0)
                            != null) {
                        String serviceName =
                                view.getServiceList().
                                        getSelectionModel().
                                        getSelectedItems().get(0);
                        String serviceURL = dataBean.getServiceURL(serviceName);
                        view.setServiceURLText(serviceURL);
                    }
                }
            }
        }
    }
*/
    /** Keeps track of download progression and errors. */
    private class DownloadListener implements ProcessorListener, Runnable {

        private String message;

        private synchronized void setMessage(String message) {
            this.message = message;
        }

        private synchronized String getMessage() {
            return this.message;
        }

        @Override
        public void run() {
            //view.setStatusBarText(getMessage());
        }

        @Override
        public void receivedException(ProcessorEvent pe) {
            setMessage(
                I18n.format(
                "status.error",
                pe.getException().getMessage()));
            Platform.runLater(this);
        }

        @Override
        public void receivedMessage(ProcessorEvent pe) {
            setMessage(pe.getMessage());
            Platform.runLater(this);
        }
    }
}
