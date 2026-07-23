#!/usr/bin/env python3
"""Upload and enable Milvus/Prometheus/Grafana autostart on CentOS VM."""
import os
import sys
from pathlib import Path

import paramiko

HOST = "127.0.0.1"
PORT = 2222
USER = "root"
PASSWORD = os.environ.get("VM_SSH_PASSWORD", "1234")
LOCAL = Path(__file__).resolve().parent


def main() -> None:
    transport = paramiko.Transport((HOST, PORT))
    transport.connect(username=USER, password=PASSWORD)
    sftp = paramiko.SFTPClient.from_transport(transport)

    uploads = [
        (LOCAL / "start-milvus-stack.sh", "/usr/local/bin/start-milvus-stack.sh", 0o755),
        (LOCAL / "start-observability-stack.sh", "/usr/local/bin/start-observability-stack.sh", 0o755),
        (LOCAL / "milvus-stack.service", "/etc/systemd/system/milvus-stack.service", 0o644),
        (LOCAL / "factory-observability.service", "/etc/systemd/system/factory-observability.service", 0o644),
    ]
    for src, dst, mode in uploads:
        print(f"put {src.name} -> {dst}")
        sftp.put(str(src), dst)
        sftp.chmod(dst, mode)
    sftp.close()
    transport.close()

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=15)
    cmd = """
set -e
docker update --restart=unless-stopped milvus-etcd milvus-minio milvus-standalone
docker update --restart=unless-stopped factory-demo-prometheus factory-demo-grafana
systemctl daemon-reload
systemctl enable docker
systemctl enable milvus-stack.service factory-observability.service
systemctl start milvus-stack.service
systemctl start factory-observability.service
echo '=== enabled ==='
systemctl is-enabled docker milvus-stack factory-observability
echo '=== active ==='
systemctl is-active milvus-stack factory-observability
echo '=== restart policy ==='
docker inspect -f '{{.Name}} {{.HostConfig.RestartPolicy.Name}}' \
  milvus-etcd milvus-minio milvus-standalone factory-demo-prometheus factory-demo-grafana
echo '=== containers ==='
docker ps --filter name=milvus --filter name=factory-demo --format 'table {{.Names}}\t{{.Status}}'
"""
    stdin, stdout, stderr = client.exec_command(cmd, timeout=200)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    print(out)
    if err.strip():
        print("STDERR:", err[-2000:])
    code = stdout.channel.recv_exit_status()
    client.close()
    sys.exit(code)


if __name__ == "__main__":
    main()
