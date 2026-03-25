Get-Process java, javaw -ErrorAction SilentlyContinue |
    Where-Object { $_.MainWindowTitle -eq "Client" } |
    Stop-Process -Force
