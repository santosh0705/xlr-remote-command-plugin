package com.xebialabs.xlrelease.plugin.remotecommand;

import com.xebialabs.deployit.plugin.api.reflect.PropertyDescriptor;
import com.xebialabs.deployit.plugin.api.udm.ConfigurationItem;
import com.xebialabs.overthere.*;
import com.xebialabs.overthere.util.CapturingOverthereExecutionOutputHandler;
import com.xebialabs.overthere.util.OverthereUtils;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.xebialabs.overthere.ConnectionOptions.*;
import static com.xebialabs.overthere.OperatingSystemFamily.*;
import static com.xebialabs.overthere.OperatingSystemFamily.WINDOWS;
import static com.xebialabs.overthere.cifs.CifsConnectionBuilder.WINRM_TIMEMOUT;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.CONNECTION_TYPE;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.SUDO_USERNAME;
import static com.xebialabs.overthere.ssh.SshConnectionType.SUDO;
import static com.xebialabs.overthere.util.CapturingOverthereExecutionOutputHandler.capturingHandler;
import static java.nio.charset.StandardCharsets.UTF_8;

public class RemoteCommand {
    private static final String SCRIPT_NAME = "uploaded-script";

    private final ConnectionOptions options = new ConnectionOptions();
    private final String protocol;
    private final OperatingSystemFamily os;
    private final String extension;
    private final String lineSeparator;

    private final CapturingOverthereExecutionOutputHandler stdout = capturingHandler();
    private final CapturingOverthereExecutionOutputHandler stderr = capturingHandler();

    public RemoteCommand(ConfigurationItem remoteCommand) throws Exception {
        this.protocol = remoteCommand.getProperty("protocol");
        this.os = remoteCommand.getProperty("os");
        copyPropertiesToConnectionOptions(options, remoteCommand);
        this.extension = this.os.getScriptExtension();
        this.lineSeparator = this.os.getLineSeparator();
    }

    private void copyPropertiesToConnectionOptions(ConnectionOptions options, ConfigurationItem ci) {
        // support legacy properties
        if(ci.hasProperty("sudo") && (Boolean) (ci.getProperty("sudo"))) {
            ci.setProperty(CONNECTION_TYPE, SUDO);
            ci.setProperty(SUDO_USERNAME, "root");
        }

        // copy all CI properties to connection properties
        for (PropertyDescriptor pd : ci.getType().getDescriptor().getPropertyDescriptors()) {
            if (!pd.getCategory().equals("output")) {
                Object value = pd.get(ci);
                setConnectionOption(options, pd.getName(), value);
            }
        }
    }

    private void setConnectionOption(ConnectionOptions options, String key, Object value) {
        if (key.equals("script") || key.equals("remotePath") || key.equals("scriptLocation")) {
            return;
        }

        if (value == null || value.toString().isEmpty()) {
            return;
        }

        // support legacy properties
        if(key.equals("temporaryDirectoryPath")) {
            key = TEMPORARY_DIRECTORY_PATH;
        } else if(key.equals("timeout")) {
            key = WINRM_TIMEMOUT;
        }

        if (value instanceof Integer && ((Integer) value).intValue() == 0) {
            logger.debug("Activating workaround for DEPLOYITPB-4775: Integer with value of 0 not passed to Overthere.");
            return;
        }

        if (key.equals(JUMPSTATION)) {
            ConfigurationItem item = (ConfigurationItem) value;

            ConnectionOptions jumpstationOptions = new ConnectionOptions();
            copyPropertiesToConnectionOptions(jumpstationOptions, item);
            options.set(key, jumpstationOptions);
        } else {
            options.set(key, value);
        }
    }

    public CmdResponse executeCommand(String command, String environmentVars, String workDirectory) {
        int rc;
        try (OverthereConnection connection = Overthere.getConnection(protocol, options)) {
            rc = executeCommand(command, environmentVars, workDirectory, connection);
        } catch(Exception e) {
            publishErrorStackTrace(e);
            rc = 1;
        }
        return new CmdResponse(rc, stdout.getOutput(), stderr.getOutput());
    }

    private int executeCommand(String command, String environmentVars, String workDirectory, OverthereConnection connection) {
        String script;
        CmdLine scriptCommand;
        OverthereFile targetFile = connection.getTempFile(SCRIPT_NAME, extension);
        if (this.os == OperatingSystemFamily.WINDOWS) {
            if (environmentVars != "") {
                script = "@echo off" + this.lineSeparator + environmentVars + this.lineSeparator + command + " || exit /b";
            } else {
                script = command;
            }
        } else {
            script = environmentVars + (environmentVars == "" ? "" : " ") + command;
        }

        if (workDirectory != null && !workDirectory.trim().isEmpty()) {
            connection.setWorkingDirectory(connection.getFile(workDirectory));
        }

        OverthereUtils.write(script.getBytes(UTF_8), targetFile);
        targetFile.setExecutable(true);
        scriptCommand = CmdLine.build(targetFile.getPath());
        return connection.execute(stdout, stderr, scriptCommand);
    }

    private void publishErrorStackTrace(Exception exception){
        StringWriter stacktrace = new StringWriter();
        PrintWriter writer = new PrintWriter(stacktrace, true);
        exception.printStackTrace(writer);
        stderr.handleLine(stacktrace.toString());
    }

    private static Logger logger = LoggerFactory.getLogger(RemoteCommand.class);

    public class CmdResponse {
        public int rc;
        public String stdout;
        public String stderr;

        public CmdResponse(int rc, String stdout, String stderr) {
            this.rc = rc;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

}
