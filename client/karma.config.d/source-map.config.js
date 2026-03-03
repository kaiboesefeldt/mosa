;(function (config) {
    if (!config.webpack) {
        config.webpack = {};
    }
    config.webpack.devtool = "inline-source-map";

    if (!config.client) {
        config.client = {};
    }
    if (!config.client.mocha) {
        config.client.mocha = {};
    }
    config.client.mocha.timeout = 10000;

    const plugins = config.plugins ? config.plugins.slice() : [];
    if (!plugins.includes("karma-source-map-support")) {
        plugins.push("karma-source-map-support");
    }
    config.plugins = plugins;

    const frameworks = config.frameworks ? config.frameworks.slice() : [];
    if (!frameworks.includes("source-map-support")) {
        frameworks.unshift("source-map-support");
    }
    config.frameworks = frameworks;
})(config);
