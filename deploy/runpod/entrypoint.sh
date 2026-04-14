#!/usr/bin/env bash
set -euo pipefail

mkdir -p /var/run/sshd /root/.ssh
chmod 700 /root/.ssh

AUTH_KEYS=/root/.ssh/authorized_keys
touch "${AUTH_KEYS}"
chmod 600 "${AUTH_KEYS}"

for key_var in PUBLIC_KEY SSH_PUBLIC_KEY; do
    key_value="${!key_var:-}"
    if [ -n "${key_value}" ] && ! grep -qxF "${key_value}" "${AUTH_KEYS}"; then
        printf '%s\n' "${key_value}" >> "${AUTH_KEYS}"
    fi
done

if [ ! -f /etc/ssh/ssh_host_rsa_key ]; then
    ssh-keygen -A
fi

/usr/sbin/sshd

exec "$@"
