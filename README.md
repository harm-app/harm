# HARM

**Home Assistant Remote Media** transforma um tablet Android antigo em uma tela de mídia local controlada pelo Home Assistant.

## Comportamento

- Tela preta/neutra enquanto está ocioso
- Reprodução RTSP nativa via LibVLC/TCP das câmeras Intelbras, sem abrir a interface do HA
- Reconexão automática quando o stream deixa de avançar
- Reprodução de mídia por URL HTTP, HTTPS ou RTSP
- Login no NVR e seleção automática das câmeras encontradas
- Pré-visualização manual das câmeras selecionadas
- Acordar, ajustar brilho, parar a mídia e apagar a tela remotamente
- Inicialização automática após reiniciar o tablet
- API HTTP local protegida por token

Compatível com Android 6.0 ou superior. Testado no Galaxy Tab A SM-P555M com Android 7.1.1.

## API local

O endereço segue o formato `http://IP_DO_TABLET:8765`. O token fica na configuração local do aplicativo.

```text
GET /status?token=TOKEN
GET /camera?token=TOKEN&channel=1&timeout=30
GET /media?token=TOKEN&url=URL_CODIFICADA&timeout=30
GET /stop?token=TOKEN
GET /wake?token=TOKEN
GET /brightness?token=TOKEN&value=180
GET /screen/off?token=TOKEN
```

`timeout=0` mantém a mídia aberta até receber `/stop` ou `/screen/off`. Valores maiores encerram automaticamente após o número de segundos informado.

## Serviços no Home Assistant

- `rest_command.harm_camera`
- `rest_command.harm_media`
- `rest_command.harm_stop`
- `rest_command.harm_wake`
- `rest_command.harm_brightness`
- `rest_command.harm_screen_off`

Exemplo de ação em uma automação:

```yaml
action: rest_command.harm_camera
data:
  channel: 1
  timeout: 30
```

## Compilar

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/HARM-0.4.3-debug.apk`
