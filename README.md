# Realocador Material Intra-Rede (RMIR)

Mod de teleporte com estética tecnológica para **Minecraft 1.21.1 / NeoForge**. Viajar entre bases é cômodo. Usar o teleporte para fugir de uma luta não funciona.

## Lore

O maior avanço dessa civilização foi a **Matéria Suspensa**: matéria condensada por processos quânticos e guardada em regiões espalhadas pelo espaço e pela órbita da Terra. O **Drive Terminal Intra-Rede (DTIR)** é a infraestrutura que transforma informação e comandos em alterações físicas nessa matéria e a distribui pela rede orbital.

O Realocador é o equipamento que liga o usuário ao DTIR. Ele não leva ninguém de um ponto a outro: converte o indivíduo em flutuações quânticas, transmite essas flutuações pela rede orbital e reconstrói o corpo no destino com Matéria Suspensa.

## Conteúdo

- **Realocador Material Intra-Rede** (o bloco que antes se chamava Estação de Teleporte): máquina de 2 blocos de altura com carcaça industrial (grafite e cerâmica). Na frente fica um tambor de revólver animado, com 8 câmaras, íris mecânica e núcleo de plasma.
  - Ao colocar, você escolhe o nome dela.
  - Clique direito numa estação nova sincroniza (ativa) ela para você.
  - Clique direito numa estação sincronizada abre o terminal de seleção.
- **Localizador**: aponta a direção e a distância até uma estação vinculada.
  - Para vincular, agache e clique com ele numa estação.
  - Ele **não teleporta**.
- **Estações públicas**: um admin (OP) marca a estação e ela aparece para todos os jogadores sem precisar sincronizar. Só admin consegue quebrar uma estação pública, e explosões não a destroem.
- **Xaero's Minimap (opcional)**: cria um waypoint quando você sincroniza uma estação.

## Regras contra fuga

| Regra | Padrão |
|---|---|
| Distância máxima da estação para começar | 4 blocos |
| Bloqueio após levar dano | 5 a 15 s, conforme o golpe (15 s com 20 de dano ou mais) |
| Bloqueio após causar dano | 10 s |
| Tempo de carregamento | 5 s |
| Extra por mob hostil (raio de 8 blocos) | +1 s cada |
| Extra para trocar de dimensão | +3 s |
| Tempo máximo de carregamento | 12 s |
| Cancela se levar dano, andar mais de 1,5 bloco, quebrarem a estação, morrer ou sair | — |

## Custo em XP

- **Mesma dimensão:** 1 nível, mais 1 a cada 500 blocos, até no máximo 10 níveis.
- **Entre dimensões:** 5 níveis.
- **Cobrança:** só acontece quando o teleporte conclui. Teleporte cancelado não cobra.
- **Criativo:** não paga.

Todos os valores ficam em `config/nexusgate-server.toml` (ou `serverconfig/` dentro do mundo).

## Comandos (admin)

- `/nexusgate public true|false`: marca como pública ou privada a estação para onde você está olhando.

## Compilar

Requer o JDK 21.

```sh
./gradlew build          # jar em build/libs/nexusgate-1.0.0.jar
./gradlew test           # testes das regras
./gradlew runClient      # abre o jogo de desenvolvimento
```

Se o projeto ficar dentro do OneDrive e o Gradle reclamar de arquivos travados, defina a variável `NEXUSGATE_BUILD_DIR` com uma pasta fora do OneDrive.

As texturas e os JSONs são gerados por `python tools/gen_textures.py` e `python tools/gen_assets.py` (a geometria da carcaça fica em `tools/station_model.py`).

## Visual

- **Terminal radial**: a tela mostra a face do tambor. Escolha uma câmara (o tambor gira para a posição de disparo) e clique no núcleo para ativar. Durante a carga, a íris abre e, no último segundo, o núcleo se abre e a visão mergulha num túnel até sair do outro lado. A roda do mouse ou as setas trocam de cilindro quando há mais de 8 destinos. Enter ativa.
- **Aviso de conexão**: no primeiro quarto da carga, a tela do tambor mostra "CONECTANDO COM A **INTRA**", em letras pretas com contorno vermelho. A palavra INTRA aparece maior e pulsando.
- **Sequência de realocação no mundo**: é o que os outros jogadores veem, e você também, em terceira pessoa ou com a tela fechada.
  1. **Instabilidade:** o núcleo acumula energia e um feixe sai dele até o peito do jogador, que vira o ponto focal. O espaço em volta começa a convergir para esse ponto: o fundo é puxado para dentro, num funil que gira, com ondulações correndo para o centro e separação de cores.
  2. **Colapso:** a própria geometria do corpo é comprimida. Primeiro perde largura (mãos, cabeça e pés na frente), depois afunila e é puxada para a altura do ponto focal, até virar um ponto minúsculo. O corpo é desenhado o tempo todo e nunca some de uma vez; a matéria comprimida vira energia e a sombra encolhe junto.
  3. **Flutuações quânticas:** fragmentos geométricos que mudam de forma, aparecem em posições fantasma, se dividem e se recombinam. Eles se desprendem dos vértices do corpo que está sendo comprimido, são atraídos para o ponto focal e descem pelo feixe até o núcleo. No fim, o próprio ponto desliza pelo feixe e é absorvido pelo núcleo.
  4. **Transmissão:** a coroa acende e um feixe orbital sobe ao céu. Na partida, ele dá um clarão.
  5. **Recepção:** no destino, um feixe desce do céu, o núcleo ativa e o espaço do ponto de chegada se distorce antes de qualquer matéria aparecer. O ponto sai do núcleo pelo feixe até o local de chegada, as flutuações se agrupam nele e o corpo se expande a partir do ponto até recuperar cabeça, tronco, braços e pernas. A distorção diminui conforme a matéria é reconstruída.

  Tudo isso é só visual e segue os mesmos eventos de sempre do servidor. O jogador continua no lugar até o teleporte de verdade.
- **VFX próprios**: shaders GLSL em `assets/nexusgate/shaders/core` (núcleo, campo de energia, túnel, fenda, conversão de matéria, lente espaço-temporal e flutuações quânticas) e partículas próprias (faíscas, motes, estilhaços e anel de choque).
- Com shaderpacks (Iris/Oculus), os shaders próprios podem ficar com aparência diferente.
- A distorção do espaço (refração real da imagem) é desligada sozinha com gráfico "Fabuloso" ou com um shaderpack ativo. O resto da animação continua.

## Checklist de teste no jogo

- [ ] Colocar uma estação abre a tela de nome. O nome vem em português.
- [ ] Clicar numa estação de outra pessoa sincroniza ela e mostra a mensagem.
- [ ] O terminal lista as estações com distância e custo de XP. As que você não consegue pagar aparecem em vermelho.
- [ ] O teleporte carrega por 5 s com a barra e as partículas, e cobra o XP na chegada.
- [ ] Levar dano de um zumbi durante o carregamento cancela o teleporte. Logo depois, aparece "Interferência detectada".
- [ ] Andar 2 blocos durante o carregamento cancela.
- [ ] Quebrar a estação de destino durante o carregamento cancela.
- [ ] Ir para o Nether custa 5 XP e carrega por 8 s.
- [ ] Um OP consegue tornar uma estação pública pelo botão e pelo comando.
- [ ] Um jogador **sem OP** não vê o botão de pública e não consegue quebrar uma estação pública.
- [ ] O Localizador mostra seta e distância. Quebrando a estação, ele mostra "Sinal perdido".
- [ ] Com o Xaero's Minimap instalado, o waypoint aparece ao sincronizar. Se não aparecer, procure "Xaero" no log.

## Licença

Copyright (C) 2026 Lucas Viana.

Distribuído sob a [GNU LGPL v3.0](LICENSE), que complementa a [GNU GPL v3.0](COPYING). Você pode usar o mod em modpacks e criar addons que dependam dele. Se redistribuir uma versão modificada, mantenha os créditos e publique o código da modificação sob a mesma licença.

