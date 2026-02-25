const path = require("path");

module.exports = {
    darkMode: "class",
    content: [
        path.join(__dirname, "../resources/templates/**/*.html"),
    ],
    theme: {
        extend: {},
    },
    plugins: [],
};