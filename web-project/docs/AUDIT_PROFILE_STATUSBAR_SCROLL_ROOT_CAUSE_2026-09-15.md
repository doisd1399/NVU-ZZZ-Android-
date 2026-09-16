# Auditoria — barra de status na transição do banner de perfil

## Causa confirmada

As rotas `/driver/profile` e `/admin/fleet` já calculavam a transição do banner em `DriverLayout.tsx` e `AdminLayout.tsx` por meio de `profileChromeProgress`. Quando o banner estava visível, o cabeçalho recebia `data-nvu-hero-chrome="active"`; depois que o banner deixava a área visível, recebia `data-nvu-hero-chrome="settled"` e o cabeçalho mudava para a superfície clara/escura correspondente.

O `NvuStatusBarController`, entretanto, tratava toda a rota de perfil como se estivesse permanentemente sobre o banner e sempre aplicava `SystemBarsStyle.Dark`, que significa ícones brancos. Assim, no tema claro, quando o cabeçalho ficava branco após a rolagem, os ícones da barra continuavam brancos e perdiam contraste.

## Correção aplicada

O controlador global agora observa exclusivamente o marcador `data-nvu-hero-chrome` nas duas rotas de perfil. Enquanto o marcador é `active`, aplica ícones brancos para o banner. Quando passa a `settled`, usa a autoridade do tema interno do NVU: tema claro aplica `SystemBarsStyle.Light` (ícones dark) e tema escuro aplica `SystemBarsStyle.Dark` (ícones brancos).

A observação é limitada às rotas `/driver/profile` e `/admin/fleet`, com `MutationObserver`, `scroll` e `resize` apenas para acompanhar a transição já produzida pelos layouts. Print, Select Profile, Ranking, login e todas as demais páginas permanecem no mapeamento global normal do tema NVU.

## Validação

O teste específico da barra de status e o TypeScript passaram. O build Release será executado com a mesma identidade NVU Pro v1.2, versionCode 358, canal OTA `production-358` e certificado histórico. A validação física em Motorola permanece pendente.
