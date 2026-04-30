# Radar — App Android para motoristas

App Android (Kotlin) que analisa ofertas de corrida do Uber Driver em tempo real.

## Status
- ✅ Compila no Codemagic (debug)
- ✅ Accessibility Service com debug logs em arquivo
- ✅ Overlay sobre o Uber Driver
- ✅ Histórico no Room database
- ⏳ Calibragem do regex pendente

## Onde está o log de debug?
Após instalar e ativar o serviço, ao abrir o Uber Driver o app salva
`radar_debug.txt` em:
```
Android/data/com.kleber.radar/files/radar_debug.txt
```
Esse arquivo contém todo texto que o serviço lê da tela do Uber.
