resource "azurerm_resource_group" "memoria" {
  name     = "rg-memoria-${var.client_name}"
  location = var.location
}

resource "azurerm_virtual_network" "memoria" {
  name                = "vnet-memoria-${var.client_name}"
  address_space       = ["10.10.0.0/16"]
  location            = azurerm_resource_group.memoria.location
  resource_group_name = azurerm_resource_group.memoria.name
}

resource "azurerm_subnet" "memoria" {
  name                 = "subnet-memoria-${var.client_name}"
  resource_group_name  = azurerm_resource_group.memoria.name
  virtual_network_name = azurerm_virtual_network.memoria.name
  address_prefixes     = ["10.10.1.0/24"]
}

resource "azurerm_network_security_group" "memoria" {
  name                = "nsg-memoria-${var.client_name}"
  location            = azurerm_resource_group.memoria.location
  resource_group_name = azurerm_resource_group.memoria.name

  security_rule {
    name                       = "AllowHTTP"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "80"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "AllowHTTPS"
    priority                  = 110
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "AllowSSHFromDeployerOnly"
    priority                   = 120
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "22"
    source_address_prefix      = var.ssh_allowed_cidr
    destination_address_prefix = "*"
  }
}

resource "azurerm_public_ip" "memoria" {
  name                = "pip-memoria-${var.client_name}"
  location            = azurerm_resource_group.memoria.location
  resource_group_name = azurerm_resource_group.memoria.name
  allocation_method   = "Static"
  sku                 = "Standard"
}

resource "azurerm_network_interface" "memoria" {
  name                = "nic-memoria-${var.client_name}"
  location            = azurerm_resource_group.memoria.location
  resource_group_name = azurerm_resource_group.memoria.name

  ip_configuration {
    name                          = "internal"
    subnet_id                     = azurerm_subnet.memoria.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.memoria.id
  }
}

resource "azurerm_network_interface_security_group_association" "memoria" {
  network_interface_id     = azurerm_network_interface.memoria.id
  network_security_group_id = azurerm_network_security_group.memoria.id
}

resource "azurerm_linux_virtual_machine" "memoria" {
  name                = "vm-memoria-${var.client_name}"
  location            = azurerm_resource_group.memoria.location
  resource_group_name = azurerm_resource_group.memoria.name
  size                = var.vm_size
  admin_username      = "memoria"

  network_interface_ids = [
    azurerm_network_interface.memoria.id,
  ]

  admin_ssh_key {
    username   = "memoria"
    public_key = var.admin_ssh_public_key
  }

  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Standard_LRS"
    disk_size_gb         = 64
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "0001-com-ubuntu-server-jammy"
    sku       = "22_04-lts-gen2"
    version   = "latest"
  }

  custom_data = filebase64("${path.module}/cloud-init.yaml")
}
