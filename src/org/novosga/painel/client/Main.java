package org.novosga.painel.client;

import java.awt.SplashScreen;
import org.novosga.painel.client.config.PainelConfig;
import org.novosga.painel.model.Senha;
import org.novosga.painel.client.ui.SysTray;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javax.swing.JOptionPane;
import org.novosga.painel.client.fonts.FontLoader;
import org.novosga.painel.client.ui.Controller;
import org.novosga.painel.client.network.PacketListener;
import org.novosga.painel.client.network.PacketListenerFactory;
import org.novosga.painel.event.SenhaEvent;

/**
 * Novo SGA Painel Main class
 * @author rogeriolino
 */
public class Main extends Application {
    
    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private static final ResourceBundle bundle = ResourceBundle.getBundle("org.novosga.painel.i18n.messages", Locale.getDefault());
    public static final Map<String,String> locales = new HashMap<String,String>();
    public static final Map<Integer,String> intervals = new HashMap<Integer,String>();
    {
        locales.put("en", "English");
        locales.put("es", "Español");
        locales.put("pt", "Português");
        
        intervals.put(-1, _("nao_exibir_filme"));
        intervals.put(10, _("segundos", 10));
        intervals.put(30, _("segundos", 30));
        intervals.put(45, _("segundos", 45));
        intervals.put(60, _("minutos", 1));
        intervals.put(120, _("minutos", 2));
        intervals.put(180, _("minutos", 3));
    }
    
    public static final String version = "{version}";
    public static final String DEFAULT_PROTOCOL = "UDP";
    public static final int DEFAULT_RECEIVE_PORT = 8888;
    public static final int DEFAULT_SEND_PORT = 9999;
    
    private PacketListener listener;
    private PainelService service;
    private PainelFx painel;
    private Controller controller;
    private PainelConfig config;

    @Override
    public void start(Stage stage) throws Exception {
        Platform.setImplicitExit(false);
        config = new PainelConfig();
        
        // Carregar configurações do Logger
        try {
            LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("logger.properties"));
        } catch (Exception e) {
            // Nunca deve acontecer, ja que o arquivo logger.properties deve estar dentro do proprio .jar
            LOG.log(Level.SEVERE, _("erro_carregando_log"), e);
        }
        
        // Carrega a configuracao do painel
        boolean configOk = false;
        try {
            config.load();
            configOk = true;
        } catch (Exception e) {
        }
        
        FontLoader.registerAll();
        
        // Inicia o servidor para receber mensagens
        String protocol = config.get(PainelConfig.KEY_PROTOCOL).getValue();
        try {
            painel = new PainelFx(this);
            Stage painelStage = new Stage();
            painelStage.initOwner(stage);
            painel.start(painelStage);
            
            listener = PacketListenerFactory.create(
                    protocol, 
                    config.get(PainelConfig.KEY_PORT_RECEIVE, Integer.class).getValue(),
                    config.get(PainelConfig.KEY_PORT_SEND, Integer.class).getValue(),
                    config.get(PainelConfig.KEY_SERVER).getValue()
            );
            listener.setOnNovaSenhaEvent(new SenhaEvent() {
                @Override
                public void handle(Senha senha) {
                    painel.chamaSenha(senha);
                }
            });
            
            listener.inicia();
            
            service = new PainelService(this);
            controller = new Controller(this, bundle);
            controller.getStage().initOwner(stage);
            try {
                service.registerAndLoad(new Runnable() {
                    @Override
                    public void run() {
                        controller.update();
                    }
                });
            } catch (Exception e) {
            } finally {
                controller.update();
            }
            
            if (configOk) {
                painel.show();
            } else {
                controller.show();
            }
            try {
                URL iconUrl = new URL("file:ui/img/tray.png");
                // adicionando icone as janelas
                Image icon = new Image(iconUrl.toExternalForm());
                controller.getStage().getIcons().add(icon);
                painel.getStage().getIcons().add(icon);
                try {
                    // Adiciona o painel na banjeida do sistema
                    new SysTray(this, iconUrl);
                } catch (Exception e) {
                    LOG.severe(e.getMessage());
                }
            } catch (MalformedURLException e) {
                LOG.severe("Imagem do ícone das janelas e systray não encontrada!");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, e.getMessage(), e);
            final String message = e.getMessage();
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(null, message, _("erro"), JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            });
        } finally {
            if (SplashScreen.getSplashScreen() != null) {
                SplashScreen.getSplashScreen().close();
            }
        }
    }

    public PainelFx getPainel() {
        return painel;
    }

    public Controller getController() {
        return controller;
    }

    public PainelConfig getConfig() {
        return config;
    }

    public PacketListener getListener() {
        return listener;
    }

    public PainelService getService() {
        return service;
    }

    /**
     * The main() method is ignored in correctly deployed JavaFX application.
     * main() serves only as fallback in case the application can not be
     * launched through deployment artifacts, e.g., in IDEs with limited FX
     * support. NetBeans ignores main().
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
    
    public static File getWorkingDirectory() {
        // pega o diretorio local, se nao puder escrever tenta o diretorio do usuario ou um temporario
        File workingDirectory = new File(".");
        if (!workingDirectory.canWrite()) {
            final String applicationName = "PainelSGA";
            try {
                final String userHome = System.getProperty("user.home", ".");
                final String sysName = System.getProperty("os.name").toLowerCase();
                if (sysName.contains("linux") || sysName.contains("solaris")) {
                    workingDirectory = new File(userHome, '.' + applicationName + '/');
                } else if (sysName.contains("windows")) {
                    final String applicationData = System.getenv("APPDATA");
                    if (applicationData != null) {
                        workingDirectory = new File(applicationData, "." + applicationName + '/');
                    } else {
                        workingDirectory = new File(userHome, '.' + applicationName + '/');
                    }
                } else if (sysName.contains("mac")) {
                    workingDirectory = new File(userHome, "Library/Application Support/" + applicationName);
                } else {
                    workingDirectory = new File(".");
                }
            } catch (java.security.AccessControlException e) {
                try {
                    workingDirectory = new File(System.getProperty("java.io.tmpdir"));
                } catch (java.security.AccessControlException e2) {
                    try {
                        workingDirectory = File.createTempFile("", applicationName);
                    } catch (java.io.IOException e3) {
                    }
                }
            }
            if (workingDirectory == null || (!workingDirectory.exists() && !workingDirectory.mkdirs())) {
                throw new RuntimeException("The working directory could not be created: " + workingDirectory);
            }
        }
        return workingDirectory;
    }
    
    public static String _(String message, Object ...args) {
        if (bundle.containsKey(message)) {
            String s = bundle.getString(message);
            return MessageFormat.format(s, args);
        }
        return message;
    }
    
}
