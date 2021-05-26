# Copyright (C) 2019 - present Juergen Zimmermann, Hochschule Karlsruhe
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.

# "Param" muss in der 1. Zeile sein
Param (
    [string]$duration = "100"
)

Set-StrictMode -Version Latest

write-host "keystore.p12 wird erstellt"
$alias = "microservice"
$keystoreDir = "src\main\resources"
$password = "zimmermann"
$email = "Juergen.Zimmermann@HS-Karlsruhe.de"
$name = "Juergen Zimmermann"
$rechnername = $env:COMPUTERNAME
$rechnernameMitDomain = "${rechnername}.${env:USERDNSDOMAIN}"
keytool -genkeypair -v -alias $alias `
   -keystore $keystoreDir\keystore.p12 `
   -storepass $password -keypass $password -validity $duration `
   -keyalg RSA -keysize 8192 -sigalg SHA512withRSA `
   -dname "EMAILADDRESS=$email,CN=$name,OU=Softwarearchitektur\, Software Engineering,O=Hochschule Karlsruhe,L=Karlsruhe,S=Baden Wuerttemberg,C=de" `
   -ext SAN=DNS:"$rechnername",DNS:"$rechnernameMitDomain",IP:127.0.0.1

write-host "certificate.cer wird erstellt"
$certDir = "config"
keytool -v -exportcert -file $certDir\certificate.cer `
    -alias $alias `
    -keystore $keystoreDir\keystore.p12 `
    -storepass $password
    
write-host "truststore.p12 wird erstellt"
$aliasConfig = "config"
$certDirConfig = "..\config\config"
keytool -importcert -v -noprompt `
    -alias $aliasConfig -file $certDirConfig\certificate.cer `
    -keystore $keystoreDir\truststore.p12 `
    -storepass $password
keytool -importcert -v -noprompt `
    -alias $alias -file $certDir\certificate.cer `
    -keystore $keystoreDir\truststore.p12 `
    -storepass $password
