# Radar — App Android para Motoristas Uber

App Android nativo (Kotlin) que analisa ofertas de corrida do Uber Driver em tempo real, classificando como **vale a pena**, **analisar** ou **recusar** com base em rentabilidade real (descontando custos de combustível, manutenção, seguro etc.).

Inspirado no app GigU. Para uso pessoal (não publicado na Play Store ainda).

## Estado atual do projeto

✅ **Funcionando:**
- Compilação no Codemagic CI/CD
- Estrutura de banco de dados (Room)
- UI básica (Dashboard, Histórico, Configurações)
- Accessibility Service ativo
- Permissões básicas

⚠️ **Em ajuste:**
- Regex do Accessibility Service para ler a tela do Uber Driver em pt-BR
- Permissão `SYSTEM_ALERT_WINDOW` (overlay) — adicionada na última versão
- Feedback visual ao salvar configurações (Toast pendente)

❌ **Pendente:**
- Calibração com tela real do Uber Driver (formato confirmado: `R$ X,XX`, `Viagem de N minutos (X.X km)`)
- Redesign visual mais polido
- Keystore de produção e publicação na Play Store

## Stack técnica

- **Linguagem:** Kotlin
- **Build:** Gradle 8.2 + Android Gradle Plugin 8.2.0
- **Min SDK:** 26 (Android 8.0) | **Target SDK:** 34 (Android 14)
- **Persistência:** Room Database + DataStore (preferences)
- **Arquitetura:** MVVM com LiveData + Coroutines
- **Navegação:** Navigation Component + BottomNavigationView
- **Build remoto:** Codemagic (Mac mini M2)

## Como funciona

1. **Accessibility Service** (`RadarAccessibilityService`) escuta eventos da janela do app `com.ubercab.driver`
2. Extrai todo o texto visível e aplica regex para identificar:
   - Valor da corrida (`R$ X,XX`)
   - Distância da viagem (`Viagem de N minutos (X.X km)`)
   - Tempo da viagem
3. **TripAnalyzer** calcula R$/km, R$/hora e lucro real (subtraindo custos definidos pelo usuário)
4. Classifica em GREEN/YELLOW/RED conforme metas mínimas
5. Mostra **OverlayService** sobreposto à tela do Uber + fala o resultado via TTS
6. Salva no Room para histórico/dashboard

## Estrutura

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/kleber/radar/
│   │   ├── accessibility/
│   │   │   └── RadarAccessibilityService.kt   # Lê tela do Uber
│   │   ├── data/
│   │   │   ├── db/         # Room database
│   │   │   ├── model/      # Trip, UserSettings
│   │   │   └── repository/ # TripRepository
│   │   ├── service/
│   │   │   ├── OverlayService.kt    # Janela flutuante
│   │   │   └── BootReceiver.kt
│   │   ├── ui/
│   │   │   ├── home/       # Dashboard (Fragment + ViewModel)
│   │   │   ├── history/    # Histórico de corridas
│   │   │   ├── settings/   # Configurações de custos/metas
│   │   │   └── MainActivity.kt
│   │   └── util/
│   │       ├── TripAnalyzer.kt     # Lógica de classificação
│   │       └── SettingsManager.kt  # DataStore wrapper
│   └── res/
│       ├── layout/   # XML das telas
│       ├── values/   # Strings, cores, temas
│       ├── menu/     # Bottom nav
│       ├── navigation/
│       ├── xml/      # accessibility_service_config
│       └── drawable/
├── build.gradle
build.gradle (root)
settings.gradle
codemagic.yaml
```

## Pontos de atenção

1. **`RadarAccessibilityService.kt`** — o regex foi calibrado com base no print de tela em pt-BR. Pode precisar ajustes para outros formatos (Uber Black, Uber Comfort, etc.) ou outros apps (99, InDriver).

2. **`OverlayService`** usa `TYPE_APPLICATION_OVERLAY` (Android 8+). Requer permissão `SYSTEM_ALERT_WINDOW` concedida manualmente pelo usuário.

3. **Permissões restritas no Android 13+** — quando instalado fora da Play Store, o usuário precisa ativar "Permitir configurações restritas" antes de conceder Acessibilidade e Overlay.

4. **Build via Codemagic** — não há keystore configurado, build é debug. Para produção precisa adicionar `keystore_reference` no `codemagic.yaml` + assinatura.

5. **MPAndroidChart** — listei na dependência mas não está sendo usada ainda (pretendia usar para gráficos no Dashboard). Pode remover se não for usar.

## Build local (alternativa ao Codemagic)

```bash
# Pré-requisitos: Android Studio Hedgehog+ ou JDK 17 + Android SDK
./gradlew assembleDebug
# APK gerado em: app/build/outputs/apk/debug/app-debug.apk
```

## Calibragem do regex (referência)

Tela atual do Uber Driver pt-BR (UberX, Garopaba-SC):

```
UberX
R$ 6,52
★ 4,88 (635)  Verificado
9 minutos (5.5 km) de distância
R. Ismael Lobo, Garopaba
Viagem de 3 minutos (0.6 km)
Rua Lajeado, 609, Garopaba - SC, 88495-000, Brasil
```

Regex aplicado:
```kotlin
val valueRegex = Regex("""R\$\s*([\d.,]+)""")
val tripDistanceRegex = Regex("""Viagem de \d+\s*minutos?\s*\(([\d.,]+)\s*km\)""")
val tripMinutesRegex = Regex("""Viagem de (\d+)\s*minutos?""")
```
