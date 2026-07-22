output "public_ip_address" {
  description = "IP publique de la VM -- a pointer depuis le DNS du client (ex. memoria.episen.fr)."
  value       = azurerm_public_ip.memoria.ip_address
}

output "resource_group_name" {
  value = azurerm_resource_group.memoria.name
}

output "vm_name" {
  value = azurerm_linux_virtual_machine.memoria.name
}
