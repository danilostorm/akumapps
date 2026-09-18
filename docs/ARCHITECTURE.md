# Arquitetura

## MainActivity

Responsável por:

- interface;
- player Kick/Twitch;
- troca áudio/vídeo;
- fallback;
- configurações;
- fullscreen;
- Picture-in-Picture.

## StreamPlaybackService

Foreground Service responsável por:

- manter a sessão do aplicativo viva em segundo plano;
- notificação persistente;
- controles Play/Pause/Assistir/Fechar.

## Smart Live Engine

Na v1.0.0 o motor é propositalmente simples:

- checagem Kick via endpoint público do canal;
- checagem Twitch via imagem de preview;
- prioridade Kick → Twitch;
- polling de 30 segundos;
- troca controlada para não reconstruir a interface inteira.

Futuras versões podem separar esse motor em módulos próprios sem alterar a UI.
