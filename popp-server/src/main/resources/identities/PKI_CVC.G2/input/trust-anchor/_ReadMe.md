# Introduction

This directory contains manually inserted public keys, acting as anchor of trust.
All other public keys and certificates in the PKI are deduced from these anchors.
These keys (and other CVC-Root-CA data) could be downloaded from the
[Atos web side][Atos].

## Structure of file names
The names of files in this directory **SHALL** follow the following structure:
`prefix suffix` as follows:
1. `prefix` **SHALL** contain eight octet, i.e. 16 hexadecimal digits containing
   the certification authority reference (CAR).
2. `suffix` **SHALL** be the string "_ELC-PublicKey".

## Structure of file content
The content of files with signature verification keys **SHALL** contain
a public key for elliptic curve cryptography, encoded (binary) in
ISO/IEC 7816 format, e.g. (for better readability the following example with
DEZGW 8-2-02-16 key shows the TLV-structure):

```
7f49 4e
   06 09 2b2403030208010107
   86 41 04a42ee03e1e077b5db4dc347d3e22ce02ac3f44f0ad583ecb2f57e69ec96089da78b619056e17932fe64b1b41e21c05ee546d2909dc357e35612e1a2479c10d55
```

## Current content
Currently, the following keys are present:
1. `4445475858820214`, DEGXX 8-2-02-14: RU, TU
2. `44455a4757820216`, DEZGW 8-2-02-16: PU

[Atos]:https://pki.atos.net/egk
