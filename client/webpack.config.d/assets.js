const path = require("path");

config.module.rules.push({
  test: /\.png$/i,
  type: "asset/inline"
});

config.resolve.alias = {
  ...(config.resolve.alias || {}),
  "@assets": path.resolve(__dirname, "..", "..", "..", "..", "client", "src", "jsMain", "resources")
};
