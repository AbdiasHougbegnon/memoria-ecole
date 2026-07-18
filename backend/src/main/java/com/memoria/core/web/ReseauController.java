package com.memoria.core.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

// Le flux mobile QR code (Phase 2) a besoin de l'IP locale du PC pour que le
// telephone puisse joindre le serveur de dev sur le meme reseau Wi-Fi. Avant,
// l'utilisateur devait la copier lui-meme depuis ipconfig/ifconfig ; ce
// endpoint la detecte a la place, cote serveur, ou l'utilisateur peut de
// toute facon la lire (contrairement au navigateur, qui n'y a pas acces pour
// des raisons de vie privee).
@RestController
@RequestMapping("/api/v1/reseau")
public class ReseauController {

    @GetMapping("/adresse-locale")
    public AdresseLocaleResponse obtenirAdresseLocale() {
        return new AdresseLocaleResponse(detecterAdresseLocale());
    }

    // Docker Desktop/WSL2/Hyper-V ajoutent des cartes reseau virtuelles
    // (vEthernet, bridges Docker...) qui portent aussi une adresse "site
    // locale" mais ne sont jamais joignables depuis un telephone sur le
    // meme Wi-Fi. isVirtual() de l'API Java ne les detecte pas (ce flag
    // concerne les alias d'interface, pas les cartes virtuelles) : on les
    // ecarte donc par mot-cle, avec repli sur la premiere trouvee si
    // aucune carte "reelle" n'est identifiee.
    private static final List<String> MOTS_CLES_VIRTUELS = List.of(
            "virtual", "vethernet", "hyper-v", "vmware", "virtualbox", "docker", "tap-windows"
    );

    private String detecterAdresseLocale() {
        List<String> candidatsVirtuels = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface reseau = interfaces.nextElement();
                if (!reseau.isUp() || reseau.isLoopback()) {
                    continue;
                }
                String nom = (reseau.getDisplayName() + " " + reseau.getName()).toLowerCase(Locale.ROOT);
                boolean estVirtuelle = MOTS_CLES_VIRTUELS.stream().anyMatch(nom::contains);

                Enumeration<InetAddress> adresses = reseau.getInetAddresses();
                while (adresses.hasMoreElements()) {
                    InetAddress adresse = adresses.nextElement();
                    if (adresse instanceof Inet4Address && adresse.isSiteLocalAddress()) {
                        if (!estVirtuelle) {
                            return adresse.getHostAddress();
                        }
                        candidatsVirtuels.add(adresse.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            return null;
        }
        return candidatsVirtuels.isEmpty() ? null : candidatsVirtuels.get(0);
    }
}
