# Akuma Stream Club

Aplicativo Android independente para acompanhar uma live com **Kick + Twitch**, fallback automático, áudio em segundo plano e modo vídeo.

## v1.0.0

- Kick como plataforma principal.
- Twitch como fallback.
- Modo **AUTO**, **KICK** e **TWITCH**.
- Checagem de disponibilidade a cada 30 segundos.
- Áudio continua em segundo plano com Foreground Service.
- Notificação nativa com Reproduzir / Pausar / Assistir / Fechar.
- Botão **Assistir Agora** para alternar entre modo áudio e vídeo.
- Fullscreen do player.
- Picture-in-Picture opcional no modo vídeo.
- Configuração dos canais dentro do app.
- Qualidade preferida da Kick configurável.
- Sem dependência do WordPress ou do akumanimes.com.

## Como funciona

O aplicativo usa os players oficiais incorporados da Kick e da Twitch. Em modo AUTO:

1. verifica Kick;
2. se Kick estiver offline e Twitch estiver ao vivo, troca para Twitch;
3. se a opção "Voltar automaticamente para Kick" estiver ligada, retorna à Kick quando ela voltar.

O áudio em segundo plano é mantido pelo player Android/WebView junto a um Foreground Service. A notificação mantém o processo ativo e fornece controles rápidos.

## Canais padrão

- Kick: `danilostorm`
- Twitch: `danilostorm`

Os dois podem ser alterados em **Configurações**.

## Build

O GitHub Actions gera automaticamente:

- `Akuma-Stream-Club-debug-apk`
- `Akuma-Stream-Club-debug-aab`

O projeto usa:

- Android API 23+
- targetSdk 36
- Java 17
- Android Gradle Plugin 8.10.1

## Observações

A detecção de live é independente do player e usa endpoints públicos/preview das plataformas. O player oficial continua sendo a autoridade final sobre disponibilidade, anúncios, autenticação e reprodução.

Não há código para remover anúncios, contornar paywalls ou burlar restrições das plataformas.
