import subprocess

rate_values = [0.01, 0.1, 0.5]
file_names = ["failScenario", "failScenarioAndUpdatable"]
configurations = [
    ("config-failure.yml", ""),
    ("errors-failure.yml", "-errors"),
    ("cluster-node-count-failure.yml", "-count"),
    ("metrics.yml", "-metrics")
]
#"python plots/plotter.py plots/config-five.yml data "failScenarioAnd.*fail_frequency-0.1.*new_node_frequency-0.1.*"  failScenario data/img/failscenarion  "Scenario 9: ""
for first_rate in rate_values:
    for second_rate in rate_values:
        for file in file_names:
            for (config, suffix) in configurations:
                pattern = "\"{0}_.*ξ-{1}.*τ-{2}.*\"".format(file, first_rate, second_rate)
                name = "{0}".format(file, first_rate, second_rate, suffix)
                prefix_command = "python plots/plotter.py plots/{0} data".format(config)
                command = "{0} {1} {2} data/img/{3}{4} \"Scenario 10\"".format(prefix_command, pattern, name, file, suffix)
                subprocess.call(command, shell=True)