variable "client_name" {
  description = "Nom court du client, utilise pour prefixer les ressources (ex. episen)."
  type        = string
}

variable "domain_name" {
  description = "Domaine dedie a l'instance de ce client (ex. memoria.episen.fr)."
  type        = string
  default     = "memoria.episen.fr"
}

variable "location" {
  description = "Region Azure de deploiement."
  type        = string
  default     = "francecentral"
}

variable "vm_size" {
  description = "Taille de la VM -- Standard_B2s suffit pour un premier client (2 vCPU, 4 Go RAM)."
  type        = string
  default     = "Standard_B2s"
}

variable "admin_ssh_public_key" {
  description = "Cle publique SSH pour l'acces administrateur a la VM."
  type        = string
}

variable "ssh_allowed_cidr" {
  description = "Plage IP autorisee a se connecter en SSH (ex. l'IP du deployeur, jamais 0.0.0.0/0)."
  type        = string
}

# Aucune variable secrete applicative (JWT, cles Azure AI) n'est declaree ici :
# ces secrets vivent dans un .env copie separement sur la VM (scp manuel ou
# etape CI/CD future), jamais dans le state Terraform (stocke en clair) ni
# dans le cloud-init.
