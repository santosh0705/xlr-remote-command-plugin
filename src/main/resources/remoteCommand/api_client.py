import io
import sys
import com.xebialabs.xlrelease.plugin.remotecommand.RemoteCommand as RemoteCommand

class APIClient(object):

    def __init__(self, host, command, environment_vars, work_dir):
        command = command.strip() if command else ""
        environment_vars = environment_vars.strip() if environment_vars else ""
        if not command:
            raise Exception("Command is not given.")

        self.remote = RemoteCommand(host)
        self.command = command
        self.environment_vars = environment_vars
        self.work_dir = work_dir

    def execute_command(self):
        response = self.remote.executeCommand(self.command, self.environment_vars, self.work_dir)
        APIClient.print_logs(response)
        return response

    @staticmethod
    def print_logs(response):
        if response.rc == 0:
            print "```"
            print response.stdout
            print "```"
        else:
            print "Exit code: "
            print response.rc
            print
            print "#### Output:"
            print "```"
            print response.stdout
            print "```"

            print "----"
            print "#### Error stream:"
            print "```"
            print response.stderr
            print "```"
            print

            sys.exit(response.rc)
