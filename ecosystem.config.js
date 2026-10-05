module.exports = {
  apps: [
    {
      name: "backend",
      cwd: "D:/BlueStreams/bluestreams-backend/code-with-quarkus",
      script: "C:/Program Files/Eclipse Adoptium/jdk-21.0.6.7-hotspot/bin/java.exe",
      args: [
        "-jar",
        "target/quarkus-app/quarkus-run.jar"
      ],
      interpreter: "none"
    }
  ]
};