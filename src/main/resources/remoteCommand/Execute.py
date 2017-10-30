from remoteCommand.api_client import APIClient

host = task.pythonScript.getProperty("host")
command = task.pythonScript.getProperty("command")
environment_vars = task.pythonScript.getProperty("environmentVars")
work_dir = task.pythonScript.getProperty("workingDirectory")
client = APIClient(host, command, environment_vars, work_dir)

response = client.execute_command()

output = response.stdout
error = response.stderr
