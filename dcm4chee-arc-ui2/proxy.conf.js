module.exports = {
  "/dcm4chee-arc/ui2/rs/keycloak.json": {
    "target": "http://localhost:4200",
    "secure": false,
    "logLevel": "debug",
    "pathRewrite": {
      "^/dcm4chee-arc/ui2/rs/keycloak.json": "/assets/keycloak.json"
    }
  },
  "/dcm4chee-arc/ui2/rs/dcm4chee-arc": {
    "target": "http://localhost:4200",
    "secure": false,
    "logLevel": "debug",
    "pathRewrite": {
      "^/dcm4chee-arc/ui2/rs/dcm4chee-arc": "/assets/dcm4chee-arc.json"
    }
  },
  "/dcm4chee-arc/ui2/rs": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "debug"
  },
  /* As the schemas are not part of the same ui project and are normally copied to the UI project by `mvn install`,
   * we need to proxy them to the dcm4chee-arc-ui2 project from the running (other) UI season */
  "/assets/schema":{
    target: "http://localhost:18080",
        secure: false,
      changeOrigin: true,
      logLevel: "debug",
      pathRewrite: { "^/assets/schema": "/dcm4chee-arc/ui2/en/assets/schema" }
  }
};

(()=>{
  [
    "/configuration/*",
    "/statistics/*",
    "/dicom-route",
    "/workflow-management",
    "/xds",
    "/lifecycle-management",
    "/monitoring/*",
    "/correct-data/*",
    "/audit-record-repository/*",
    "/migration/*",
    "/agfa-migration/*",
    "/study/*",
    "/permission-denied",
    "/device/*",
    "/configuration/*"
  ].forEach(path=>{
    module.exports[path] = {
      target: "http://localhost:4200",
      secure: false,
      logLevel: "debug",
      bypass: function (req, res, proxyOptions) {
        if (req.url.match(/\.(js|css|json|ico|png|jpg|svg)$/)) {
          return; // do not redirect, serve file normally
        }

        if (req.headers.accept && req.headers.accept.includes("text/html")) {
          console.log("[HPM] SPA fallback for", req.url);
          return "/index.html";
        }
      }
    }
  })
})()
