module.exports = {
    apps: [
        {
            name: "backend",

            cwd: "D:/BlueStreams/bluestreams-backend/code-with-quarkus",

            script: "java",

            args: "-jar target/quarkus-app/quarkus-run.jar",

            env: {
                NODE_ENV: "production",
                QUARKUS_PROFILE: "prod"
            },

            autorestart: true,
            watch: false,

            max_restarts: 10,
            restart_delay: 5000,

            time: true
        }
    ]
};