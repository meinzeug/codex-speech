
module.exports = {
    apps: [
        {
            name: "codex-backend",
            script: "./.venv/bin/uvicorn",
            args: "main:app --host 0.0.0.0 --port 8000",
            cwd: "./apps/backend",
            interpreter: "none",
            env: {
                PYTHONPATH: "."
            }
        }
    ]
};
