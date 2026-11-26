# Realocador Material Intra-Rede (RMIR) — Arquitetura

Mapa técnico do código. Para o que o mod faz do ponto de vista do jogador, veja o [README](README.md). Para o porquê das decisões, veja o [design](docs/superpowers/specs/2026-11-21-nexus-gate-design.md).

- **Nome:** o jogador vê "Realocador Material Intra-Rede (RMIR)". Os ids internos continuam `nexusgate`, `teleport_station` e o pacote `com.nexusgate`, para não quebrar mundos salvos.
- **Plataforma:** Minecraft 1.21.1, NeoForge, Java 21.
- **Mod id:** `nexusgate`. Pacote raiz: `com.nexusgate`.
- **Princípio central:** o servidor decide tudo. O cliente só desenha. Toda regra (distância, combate, XP, permissão) é checada de novo no servidor, mesmo que a tela já a mostre.

## Pacotes

| Pacote | Lado | Papel |
|---|---|---|
| `com.nexusgate` | ambos | `NexusGate.java`: ponto de entrada. Registra tudo e liga os eventos. |
| `core` | ambos | Regras puras, sem Minecraft: validação, tempo de carga, XP, combate, ordenação. É o que os testes cobrem. |
| `station` | servidor | Persistência: a rede de estações (`StationNetwork`) e as estações que cada jogador ativou (`ActivatedStations`). |
| `teleport` | servidor | Orquestração: ciclo de vida das estações, sessões de carga, execução do teleporte, bloqueio de combate e envio de eventos visuais. |
| `block`, `item` | ambos | O bloco de 2 de altura, o block entity e o Localizador. |
| `net` | ambos | Payloads de rede e o registro deles. |
| `registry` | ambos | `DeferredRegister`s: blocos, itens, block entity, componentes, attachments, partículas, aba criativa. |
| `config` | ambos | `NexusConfig` (servidor) e `NexusClientConfig` (cliente). |
| `command` | servidor | `/nexusgate public true\|false`. |
| `client` | cliente | Ligação do cliente, handlers de payload, tela de nome, integração com o Xaero. |
| `client/gui` | cliente | O terminal radial (tambor de revólver) e o desenho vetorial dele. |
| `client/fx` | cliente | O cérebro dos efeitos visuais, o mergulho no núcleo, o HUD, os efeitos no mundo e os sons de interface. |
| `client/render` | cliente | Renderer animado da estação (tambor, íris, núcleo), shaders próprios e render types. |
| `client/particle` | cliente | Partículas próprias: faísca, mote, estilhaço, anel de choque e flutuação quântica (`QuantumParticle`, que segue um caminho corpo → feixe → núcleo ou o inverso). |

O código cliente só é carregado no cliente. `NexusGate` chama `ClientSetup.init` apenas quando `FMLEnvironment.dist.isClient()`. Os handlers de payload que vão para o cliente são lambdas que chamam `ClientPayloadHandlers`, então o servidor dedicado nunca carrega essas classes.

## Dados e persistência

- **`StationRecord`** (record imutável): `id`, `name`, `dimension`, `pos` (metade de baixo), `owner`, `isPublic` e `icon` (de 0 a 11, sendo 0 o pino). As alterações usam `withName`, `withPublic` e `withIcon`.
- **`StationNetwork`** (`SavedData` no overworld, arquivo `nexusgate_stations`) guarda todas as estações do servidor, indexadas por UUID.
- **`ActivatedStations`** é um attachment de jogador (`activated_stations`) com o conjunto de UUIDs que ele sincronizou. Persiste e sobrevive à morte (`copyOnDeath`).
- **`TeleportStationBlockEntity`** fica na metade de baixo e liga o bloco ao `StationRecord` pelo id. Também espelha o nome, mostrado na telinha da coroa, e a flag "sem lugar seguro para chegar", que deixa a sirene vermelha. A cada 2 s o servidor checa a área de chegada de novo.
- **Limpeza:** ao abrir o terminal, os registros cujo bloco sumiu são removidos, mas só quando o chunk já está carregado. Na conclusão, o chunk de destino é carregado e conferido.

## Fluxo principal: do clique ao teleporte

```
Clique direito na estação
  └─ TeleportStationBlock → StationService.onUse
       ├─ estação desconhecida → ActivatedStations.activate + XaeroWaypointPayload
       └─ estação conhecida    → StationService.openSelection
                                   monta a lista de StationEntry (distância, custo, se dá para pagar)
                                   → OpenSelectionPayload ─────────────► GateScreen (cliente)

Jogador escolhe a câmara e clica no núcleo
  GateScreen ── SelectStationPayload ──► ChargeManager.requestStart (servidor)
       ├─ RequestValidator.validateStart  (estação de origem, alcance, destino, combate, XP)
       │     falhou → GateFx.reject (só para quem pediu)
       └─ ok → ChargeRules.chargeTicks (base + mobs hostis + troca de dimensão, com teto)
               cria a ChargeSession → GateFx.start (para quem pediu e quem está vendo)

ServerTickEvent → ChargeManager.onServerTick (cada sessão)
  ChargeSession.tick(TickInput: presente, levou/causou dano, deslocamento, origem e destino existem)
       ├─ CONTINUE → zumbido subindo
       ├─ cancelou → GateFx.cancel
       └─ COMPLETE → TeleportExecutor.targetBlockPresent (carrega o chunk de destino)
                     RequestValidator.validateCompletion (XP de novo)
                     TeleportExecutor.teleport (SafeArrival escolhe um lugar seguro ao lado ou em cima)
                     GateFx.depart (na origem) + GateFx.arrive (no destino)
                     cobra o XP só agora
```

### Bloqueio de combate (`CombatEvents`)

- Ouve `LivingIncomingDamageEvent` com prioridade `LOWEST`. Assim, golpes absorvidos por escudo, armadura ou corações de absorção contam, e eventos cancelados não contam.
- **Levar dano** bloqueia de `hurtLockMinSeconds` (5 s) até `hurtLockMaxSeconds` (15 s), em escala linear até `hurtLockFullDamage` (20 de dano).
- **Causar dano** a outra entidade bloqueia o atacante por `combatLockSeconds` (10 s).
- O tempo restante vai para o cliente (`CombatLockPayload` → `ClientCombatLock`), que deixa a sirene vermelha e mostra o aviso.
- `FOUGHT_SINCE_TICK` marca quem lutou desde o último tick. `ChargeManager` consome essa marca para cancelar a carga.

## Rede (`net/Payloads.java`)

| Payload | Direção | Para quê |
|---|---|---|
| `OpenSelectionPayload` | S → C | Abre o terminal com a origem, as permissões, o combate restante e os destinos. |
| `OpenRenamePayload` | S → C | Abre a tela de nome depois de colocar a estação. |
| `XaeroWaypointPayload` | S → C | Cria um waypoint no Xaero's Minimap, se estiver instalado. |
| `CombatLockPayload` | S → C | Ticks de bloqueio restantes. |
| `GateFxPayload` | S → C | Fase visual: `START`, `CANCEL`, `REJECT`, `DEPART`, `ARRIVE`. |
| `SelectStationPayload` | C → S | Pedido de teleporte (origem → destino). |
| `RenameStationPayload` | C → S | Renomear (dono ou OP). |
| `SetIconPayload` | C → S | Trocar o ícone (dono ou OP). |
| `SetPublicPayload` | C → S | Tornar pública ou privada (só OP, checado de novo). |

## Cliente e visual

Tudo de visual parte de **`TeleportFxController`**. Ele recebe os `GateFxPayload`, acompanha as cargas e as recepções visíveis, solta as partículas e expõe o progresso para cinco consumidores. O tempo de cada etapa vem de **`RelocationTimeline`**, que só tem funções puras e tem teste.

A sequência de realocação usa só os eventos que o servidor já mandava:
- `START` traz a duração da carga, e cada etapa é uma fração dela: conexão (0–25%), desmaterialização (25–80%) e uplink orbital (80–100%);
- `DEPART` marca o teleporte de verdade e o clarão do feixe orbital;
- `ARRIVE` começa a recepção: feixe do céu, núcleo, reconstrução (50 ticks).

O servidor não sabe que essa animação existe.


- **`StationRenderer`** (block entity renderer) é a máquina em voxels. Tem o tambor de 8 câmaras dentro do anel do estator, a íris de 8 lâminas, o núcleo de plasma, a sirene da antena e o nome na coroa. Parado, o tambor avança 45° a cada poucos segundos. Carregando, dá duas voltas, acende as lentes em cascata e abre a íris em passos. A geometria vem de `DrumMesh`, com círculos "escadinha" no grid de pixels do modelo. `StationItemRenderer` reaproveita o mesmo desenho para o item.
- **`WorldFxRenderer`** desenha no mundo: o feixe de conexão do núcleo até o jogador, a coroa acesa e o feixe orbital, o feixe que desce do céu no destino, o feixe de reconstrução e as fendas nos núcleos.
- **`MatterDissolveRenderer`** faz a desmaterialização do corpo. No `RenderPlayerEvent.Pre`, cancela o render normal e chama o mesmo renderer de novo por um buffer embrulhado. Cada camada com textura (skin, armadura, capa, itens) vai para o shader `matter_dissolve`, com a altura do vértice e a dissolução na cor do vértice. A textura de cada `RenderType` é lida pelo `META-INF/accesstransformer.cfg`. A entidade não muda em nada.
- **`HudFxLayer`** desenha o medidor radial de carga em volta da mira e a sequência de tela cheia (mergulho → túnel → saída).
- **`GateScreen`** é o terminal radial. Ele implementa `TeleportFxController.LocalListener` para seguir a carga do próprio jogador.

Classes de apoio:

- **`DiveFx`** é o "mergulho no núcleo", dividido entre a tela e o HUD para a passagem entre os dois não ter corte. No último segundo da carga, a casca do núcleo se abre em pétalas e o zoom cresce de 1x até 60x. Depois vem o túnel (o servidor move o jogador enquanto isso) e, por fim, a saída.
- **`RadialLayout`** guarda a geometria pura do tambor: qual entrada está em qual câmara, as páginas de 8, a rotação mais curta até a posição de disparo (12 h) e o hit test. Tem teste.
- **`GuiDraw`** faz o desenho vetorial: anéis, setores, discos, gradientes iluminados e pixel art.
- **`GlyphFactory`** gera um sigilo determinístico para cada estação a partir do id, igual em todos os clientes. Tem teste.
- **`StationIcons`** tem os 12 ícones em pixel art 9×9. A ordem é salva por estação, então ícone novo só pode entrar no fim da lista.
- `TechButton`, `IconButton` e `Terminal` dão o visual de painel grafite e cerâmica às telas menores (`RenameScreen`).
- **`GateSounds`** monta os sons de interface a partir de eventos vanilla em camadas, com o pitch alterado.

### Shaders (`assets/nexusgate/shaders/core`)

| Programa | Uso |
|---|---|
| `core_orb` | Esfera de plasma do núcleo (GUI e mundo). O raio precisa bater com `DiveFx`. |
| `energy_field` | Fitas, anéis e colunas de energia (aditivo). |
| `wormhole` | O túnel do mergulho. |
| `rift` | A fenda espacial que escurece o centro. |
| `matter_dissolve` | Formato de entidade. Dissolve o corpo pixel por pixel, de cima para baixo, com borda ciano. Sem ele, o corpo só some na metade da conversão. |

- **`ModShaders`** registra os programas. Se o driver recusar um deles, cada getter cai no shader vanilla `position_tex_color`, e o jogo não trava.
- **`ModRenderTypes`** define os render types do mundo. Todos testam profundidade mas não escrevem nela. Os parâmetros de cada instância vão na cor do vértice.
- **`ShaderQuads`** desenha quads imediatos com uniforms por chamada, para a GUI e o HUD.

## Configuração

`NexusConfig` (servidor, `serverconfig/nexusgate-server.toml`):

- `[charging]`: `chargeSeconds` 5, `perHostileSeconds` 1, `hostileRadius` 8, `maxChargeSeconds` 12, `crossDimensionExtraSeconds` 3, `combatLockSeconds` 10, `hurtLockMinSeconds` 5, `hurtLockMaxSeconds` 15, `hurtLockFullDamage` 20, `maxMoveDistance` 1.5, `stationUseRange` 4, `allowCrossDimension` true.
- `[xp]`: `xpCostEnabled` true, `xpBaseCost` 1, `xpBlocksPerLevel` 500, `xpMaxSameDimension` 10, `xpCrossDimensionCost` 5.
- `[admin]`: `publicStationPermissionLevel` 2.

`NexusClientConfig` (cliente): `showSelectHint`, que mostra a dica "selecione um destino" no terminal.

## Assets e ferramentas

- Os modelos do bloco (`teleport_station_lower.json` e `teleport_station_upper.json`), as texturas e parte dos JSONs são **gerados** por scripts Python em `tools/`:
  - `gen_textures.py` gera as texturas da carcaça, das partículas e do item;
  - `gen_assets.py` gera os modelos, blockstates e JSONs;
  - `station_model.py` define a geometria da carcaça e é usado por `gen_assets.py`.
- Edite os scripts e gere de novo, em vez de editar os JSONs e PNGs à mão.
- O que se mexe (tambor, íris, núcleo, luzes) não está nos JSONs. Fica em `StationRenderer`.
- Os idiomas ficam em `lang/pt_br.json` e `lang/en_us.json`. As chaves seguem o padrão `msg.nexusgate.*`.

## Testes

`./gradlew test` roda os testes JUnit de:

- `core`: `ChargeRules`, `ChargeSession`, `CombatLock`, `CombatRules`, `RequestValidator`, `XpCost`, `SafeArrival`, `StationSorter`, `LocatorArrow` e `Permissions`;
- `StationRecord`;
- `RadialLayout` e `GlyphFactory`;
- `RelocationTimeline`.

Renderização, rede e eventos não têm teste automático. Use o checklist do README no jogo (`./gradlew runClient`).

## Onde mexer para…

| Quero mudar… | Arquivo |
|---|---|
| Uma regra de teleporte (o que bloqueia, quando cancela) | `core/RequestValidator`, `core/ChargeSession`, `core/ChargeRules` |
| Um valor padrão | `config/NexusConfig` |
| O custo em XP | `core/XpCost` |
| O que acontece ao clicar ou colocar a estação | `teleport/StationService` |
| Onde o jogador chega | `core/SafeArrival` + `teleport/TeleportExecutor` |
| O terminal radial | `client/gui/GateScreen` (+ `RadialLayout`, `GuiDraw`) |
| A animação da máquina no mundo | `client/render/StationRenderer` (+ `DrumMesh`) |
| A sequência de mergulho e túnel | `client/fx/DiveFx`, `HudFxLayer`, shader `wormhole` |
| A carcaça estática do bloco | `tools/station_model.py` → `python tools/gen_assets.py` |
| Um ícone novo de estação | `client/gui/StationIcons` (só no fim) + `StationService.ICON_COUNT` |
