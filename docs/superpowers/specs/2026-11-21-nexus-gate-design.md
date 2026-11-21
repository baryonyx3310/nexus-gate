# Nexus Gate — Design

Mod de teleporte com estética tecnológica para **Minecraft 1.21.1 / NeoForge**, inspirado nas mecânicas do Waystones, mas escrito do zero e equilibrado contra o uso como "botão de fuga".

## Intenção e o porquê

O usuário gosta da ideia de uma rede de pontos de teleporte (como o Waystones), mas acha o Waystones "quebrado" porque **dá para fugir de qualquer perigo na hora** (botão no inventário, pergaminhos, teleporte instantâneo). O objetivo é um mod novo, com visual de tecnologia (metal, painéis, luz ciano, zumbido elétrico) em vez de pedra mágica, onde viajar entre bases continua cômodo, mas escapar de combate ou de situação perigosa não é possível.

Referências analisadas (na pasta do projeto):
- `waystones-neoforge-1.21.1-21.1.47.jar` (BlayTheNinth, **All Rights Reserved**) — usado só como referência de mecânica. **Nenhum código, textura, som ou nome dele pode ser copiado.**
- `xaeros_waystones_compatibility-NeoForge-1.21.1-2.1.0.jar` — addon que cria waypoint no Xaero's Minimap quando se ativa uma waystone. Inspira a integração opcional abaixo.

## Objetivos

1. Bloco **Estação de Teleporte** (Teleport Station) que forma uma rede de pontos.
2. Teleporte entre estações com regras anti-fuga (carregamento, cancelamento por dano/movimento, bloqueio de combate, mobs hostis atrapalham).
3. Tela de seleção com estética de terminal.
4. Item **Localizador** que aponta direção/distância até uma estação (não teleporta).
5. Integração opcional com Xaero's Minimap (waypoint ao ativar).
6. Textos em **pt_br** e **en_us**.

## Não-objetivos (v1)

- Custo em energia FE, combustível ou itens. **O único custo é XP** (decisão do usuário, ver "Custo em XP").
- Sharestones, warp plates, pergaminhos, warp stone, botão no inventário.
- Proteção/permissões de dono (qualquer um pode quebrar uma estação em survival).
- Grupos, favoritos, ordenação customizável.

(Estações **públicas definidas por ADM** entram no mod — decisão do usuário, ver "Estações públicas".)

## Anti-objetivos (seria fracasso mesmo funcionando)

- Qualquer forma de sair de onde você está **sem estar numa estação** (item de bolso, comando para jogador comum, botão de inventário).
- Teleporte que conclui enquanto o jogador está levando dano ou acabou de lutar.
- Depender do Waystones ou do Balm; copiar assets/código do Waystones.
- Custo diferente de XP (energia, combustível, itens).
- Jogador comum conseguir tornar uma estação pública.

## Restrições

- Minecraft **1.21.1**, NeoForge **21.1.x**, Java **21** (a máquina tem Java 26 instalado; usar toolchain do Gradle para baixar o 21).
- Build com **ModDevGradle** (template oficial NeoForge MDK 1.21.1).
- Sem dependências obrigatórias além de NeoForge. Xaero é opcional (o mod deve carregar e funcionar sem ele).
- Assets (texturas/modelos) precisam ser originais. Sons: usar sons vanilla (ex.: `block.beacon.activate`, `block.beacon.ambient`, `block.respawn_anchor.charge`, `entity.enderman.teleport`, `block.beacon.deactivate`) para não precisar de áudio próprio.

## Abordagens consideradas

| | Abordagem | Trade-off | Decisão |
|---|---|---|---|
| A | Mod próprio do zero | Mais trabalho (rede, GUI, persistência), mas é 100% do usuário, distribuível, sem licença alheia, regras anti-fuga do jeito desejado | **Escolhida pelo usuário** |
| B | Addon sobre o Waystones (mixins + reskin) | Menos trabalho, mas continua dependente do Waystones/Balm, frágil a updates, não é "um novo mod" | Rejeitada |
| C | Só config + resource pack do Waystones | Config não suporta carregamento cancelável nem bloqueio de combate; não resolve o problema | Descartada |

## Design

### Identidade (decisão do Claude, usuário pode mudar)
- Nome: **Nexus Gate**, mod id `nexusgate`, pacote `com.nexusgate` (o usuário pode trocar).
- Pasta do projeto: `nexus-gate/` dentro da pasta de trabalho atual.

### Bloco: Estação de Teleporte (`nexusgate:teleport_station`)
- 2 blocos de altura (metade inferior/superior, como porta/waystone). Quebrar uma metade quebra a outra; dropa 1 item.
- Visual: base de metal escuro com painéis e rebites, coluna central com "núcleo" ciano emissivo (textura com partes que brilham no escuro / render emissivo), topo com antena/anel. Modelo JSON em blocos (pode ser feito à mão em JSON; não exige Blockbench).
- Partículas ambiente leves (faíscas ciano ocasionais) quando a estação está ativada para o jogador local é opcional; mínimo: partículas constantes discretas.
- Dureza ~ bloco de ferro, precisa de picareta de pedra+, resistente a explosão moderada.
- **Receita** (shaped): ferro, bloco de redstone, pérola do ender, quartzo do Nether e vidro. Ex.:
  ```
  I G I
  Q E Q
  I R I
  ```
  I = barra de ferro, G = vidro, Q = quartzo, E = pérola do ender, R = bloco de redstone.

### Rede e persistência
- `SavedData` no Overworld guardando todas as estações: `UUID id`, `String name`, `ResourceKey<Level> dimension`, `BlockPos pos` (metade inferior), `UUID owner` (quem colocou), `boolean isPublic`.
- Block entity na metade inferior guarda o `UUID id` da estação.
- Por jogador (data attachment NeoForge, persistente, copiado na morte): conjunto de `UUID` de estações ativadas.
- Ao colocar: estação registrada com nome padrão `"Estação <x>, <z>"`, e o colocador já a tem ativada. Abre a tela de nomear para o colocador.
- Ao quebrar: estação removida da rede (some da lista de todos).
- Ao clicar com botão direito numa estação **não ativada**: ativa (som + mensagem "Estação <nome> sincronizada"), dispara waypoint do Xaero se presente.
- Ao clicar numa estação **já ativada**: abre a tela de seleção.
- Dono pode renomear (botão na tela de seleção quando está na própria estação). Agachar + clique direito com o Localizador = vincular (ver abaixo).

### Tela de seleção (terminal)
- Fundo escuro, borda ciano, fonte padrão do jogo, cabeçalho `> NEXUS GATE // <nome da estação atual>`.
- Lista rolável das estações ativadas pelo jogador (exceto a atual): nome, dimensão, distância (ou "—" se outra dimensão). Estações da dimensão atual primeiro, depois as outras, ordem alfabética dentro de cada grupo.
- Estação cujo bloco não existe mais é removida automaticamente da lista.
- Clicar numa entrada → envia pedido ao servidor → tela fecha → começa o carregamento.
- Se o jogador está em bloqueio de combate, as entradas aparecem desabilitadas e o cabeçalho mostra `INTERFERÊNCIA DETECTADA — <s>s`.
- Toda validação é no servidor; o cliente só exibe.

### Regras anti-fuga (núcleo do mod)
Valores padrão, todos configuráveis no arquivo de config do servidor (`nexusgate-server.toml`):

1. **Precisa estar na estação**: pedido só é aceito se o jogador está a ≤ 4 blocos da estação de origem e ela existe.
2. **Bloqueio de combate**: se o jogador **levou dano** ou **causou dano a uma entidade viva** nos últimos **10 s**, não pode iniciar. Mensagem na action bar: "Interferência detectada: aguarde Xs". (Dano de qualquer fonte conta, incluindo queda, fogo, fome.)
3. **Carregamento**: duração base **5 s**, **+1 s por mob hostil** (`Enemy`) num raio de **8 blocos**, teto de **12 s**. Teleporte entre dimensões: **+3 s** (somado antes do teto). O tempo é calculado no início.
4. **Cancelamento**: durante o carregamento, o teleporte é cancelado se o jogador (a) levar qualquer dano, (b) se afastar mais de **1,5 bloco** da posição inicial, (c) a estação de origem ou destino for quebrada, (d) desconectar ou morrer. Cancelamento por dano também inicia o bloqueio de combate (consequência natural da regra 2). Mensagem "Sincronização interrompida".
5. **Feedback do carregamento**: espiral de partículas ciano (ex.: `ELECTRIC_SPARK` + `END_ROD`) subindo ao redor do jogador, som de carga crescente, barra de progresso na action bar (`[■■■■■□□□□□] 50%`). Outros jogadores veem as partículas (avisa que alguém está saindo).
6. **Chegada**: o jogador aparece em frente à estação de destino (bloco livre adjacente; procurar posição segura nas 4 direções; se nenhuma, em cima da estação). Pets/montarias **não** são levados (v1). Jogador montado é desmontado antes do teleporte. Pequeno efeito de partículas/som na chegada.
7. Sem cooldown além do bloqueio de combate.

### Custo em XP (decisão do usuário; valores são decisão do Claude)
- Mesma dimensão: **1 nível + 1 nível a cada 500 blocos** de distância (arredondado para baixo), teto de **10 níveis**.
- Entre dimensões: **5 níveis fixos**.
- O custo é calculado e mostrado na tela de seleção ao lado de cada destino (ex.: `3 XP`). Destinos que o jogador não consegue pagar aparecem em vermelho e não podem ser escolhidos.
- O servidor verifica se o jogador tem os níveis **no início** do carregamento e **cobra só quando o teleporte conclui**. Teleporte cancelado não cobra (o cancelamento já é a punição: tempo perdido + bloqueio de combate). Se no fim o jogador não tiver mais os níveis, o teleporte falha com mensagem "XP insuficiente".
- Jogadores em criativo não pagam.
- O cálculo do custo fica numa classe pura testável.

### Estações públicas (definidas por ADM)
- Um jogador com permissão de operador (nível **2+**) pode marcar/desmarcar uma estação como **pública**:
  - Pelo botão `Tornar pública / Tornar privada` na tela de seleção, que **só aparece para OPs** (o servidor revalida a permissão).
  - Pelo comando `/nexusgate public <true|false>` olhando para a estação (permissão 2).
- Estação pública aparece na lista de **todos os jogadores** sem precisar ativar, com a etiqueta `[PÚBLICA]` e cor diferenciada (dourado).
- Teleportar para ou de uma estação pública segue **as mesmas regras anti-fuga e o mesmo custo em XP**.
- Só OP pode quebrar uma estação pública em survival (jogador comum recebe "Estação protegida pela administração"). Estações privadas continuam sem proteção.
- O campo `boolean isPublic` é salvo junto da estação no `SavedData`.

Implementação sugerida: um gerenciador servidor (`Map<UUID jogador, ChargeState>`) atualizado em `ServerTickEvent`/`PlayerTickEvent`; timestamps de combate atualizados em `LivingDamageEvent.Post` (vítima jogador e atacante jogador). A lógica de cálculo (tempo de carga, verificação de combate, verificação de movimento) deve ficar em classes puras testáveis sem Minecraft rodando.

### Item: Localizador (`nexusgate:locator`)
- Agachar + clique direito numa estação **ativada**: vincula o localizador a ela (guardado em data component).
- Na mão: action bar mostra `→ <nome>  <distância> m  <seta de direção ↑↗→↘↓↙←↖>` relativa para onde o jogador olha; em outra dimensão: `<nome> — outra dimensão`.
- **Não teleporta.** Sem uso fora disso.
- Receita (shaped): bússola no centro, barras de ferro ao redor, 1 redstone e 1 quartzo (o construtor define o desenho final).

### Integração Xaero's Minimap (opcional)
- Se `xaerominimap` estiver carregado, ao ativar uma estação cria um waypoint no cliente com o nome da estação (cor ciano), na dimensão certa. Envio via payload de rede servidor→cliente; lado cliente só toca a API do Xaero se o mod estiver presente (classe isolada, carregada condicionalmente).
- Se a API do Xaero não estiver disponível como dependência de compilação viável, implementar por reflexão ou deixar a integração para depois — o resto do mod não depende dela.

### Config (servidor)
`chargeSeconds=5`, `perHostileSeconds=1`, `hostileRadius=8`, `maxChargeSeconds=12`, `crossDimensionExtraSeconds=3`, `combatLockSeconds=10`, `maxMoveDistance=1.5`, `stationUseRange=4`, `allowCrossDimension=true`, `xpCostEnabled=true`, `xpBaseCost=1`, `xpBlocksPerLevel=500`, `xpMaxSameDimension=10`, `xpCrossDimensionCost=5`, `publicStationPermissionLevel=2`.

### Idiomas
`assets/nexusgate/lang/pt_br.json` e `en_us.json` com todos os textos (nomes de bloco/item, mensagens, tela).

### Entrega
- Projeto Gradle compilável em `nexus-gate/`.
- `.jar` final gerado (`gradlew build`) e copiado para a pasta de trabalho.
- Testes unitários (JUnit) para a lógica pura das regras anti-fuga.
- Teste manual pelo usuário via `gradlew runClient` (o Claude roda o build e os testes; jogar fica com o usuário).

## Casos de borda (decisões do Claude)
- **Chunk de destino descarregado**: o servidor força o carregamento do chunk (ticket temporário) na hora do teleporte, como o teleporte vanilla faz.
- **Localizador com estação removida**: mostra "Sinal perdido" na action bar e o vínculo é limpo.
- **Nome padrão**: localizado (chave de tradução com x/z), salvo como texto já resolvido no momento da colocação.
- **Waypoint do Xaero ao quebrar estação**: não é removido na v1 (o jogador apaga manualmente).
- **Pistões**: a estação não pode ser empurrada (`PushReaction.BLOCK`).

## Deixado para o construtor
- Estrutura interna de classes, nomes de pacotes internos, registro (DeferredRegister).
- Desenho exato das texturas (16x16, paleta metal escuro + ciano; podem ser geradas por script) e do modelo JSON.
- Desenho exato da receita do Localizador.
- Layout fino da tela (tamanhos, cores exatas), contanto que tenha estética de terminal.
- Escolha exata de partículas e sons vanilla.
- Versão exata do NeoForge 21.1.x e dos mappings Parchment.
