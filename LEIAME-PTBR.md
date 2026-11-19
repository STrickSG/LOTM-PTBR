# LOTM PTBR — LOTMCraft 1.13.3 em português

Versão pessoal do LOTMCraft 1.13.3, traduzida para o português do Brasil e com correções de bugs.
Por dentro o mod continua sendo `lotmcraft`, então mundos da 1.13.3 abrem normalmente. Na lista de
mods ele aparece como **LOTM PTBR**.

## Instalação

1. No CurseForge, crie um perfil **Minecraft 1.21.1** com **NeoForge 21.1.217** ou mais novo.
2. Instale o **MineColonies** (1.21.1) pelo CurseForge: ele é obrigatório e traz junto Structurize, BlockUI,
   Domum Ornamentum e MultiPiston. O **Stylecolonies** é opcional (mais estilos de prédio, como o Steampunk).
3. Abra a pasta do perfil (três pontinhos → *Open Folder*) e coloque o `LOTM-PTBR-1.13.3.jar` na pasta `mods`.
4. Se o LOTMCraft original estiver lá, tire-o: os dois não podem ficar juntos.
5. No jogo, escolha *Português (Brasil)* em Opções → Idioma.

## MineColonies

Os rituais usam os **colonos** do MineColonies no lugar dos aldeões.

- Fiéis (Sol 1 e Âncoras): o Registro Sagrado é usado num colono. Ele ganha "(Fiel de [nome])" no nome.
- Plateia do Feiticeiro Bizarro (Tolo 4): cada colono vale 1 ponto.
- Matar um colono também quebra o juramento do Mentor da Justiça (Sol 3).
- Cidade das Marionetes: dentro da sua colônia, agachado e de mão vazia, clique numa marionete sua com nome. Ela vira
  colono (precisa de casa livre na colônia), continua sendo sua marionete (conta no menu e no limite de marionetes),
  mas não luta nem segue: vive e trabalha na colônia. Monstros ganham forma humana, com a skin de um Beyonder Errante;
  um Beyonder Errante fica com a própria skin; aldeões ficam com a aparência de colono.

## Ice and Fire (opcional)

Sem precisar do Ice and Fire para rodar: se ele estiver instalado, os dragões de estágio 4 e 5 são semideuses.
Estágio 4 conta como Sequência 4 e estágio 5 como Sequência 3, nos rituais (selar, matar no espetáculo, alvo mais
forte...) e na regra do mod: poderes de Beyonders de Sequência 5 ou mais fraca não os afetam. Armas, flechas e
qualquer outro dano continuam ferindo normalmente. O Ice and Fire Community Edition para 1.21.1 pede NeoForge
21.1.248 ou mais novo e as bibliotecas Uranus e Jupiter.

## Tradução

- Todos os textos do arquivo de idioma (`pt_br.json`), com os nomes da nossa versão:
  Ministro (Justiciar), Feiticeiro Bizarro, Feiticeiro dos Segredos etc.
- Nomes de poção, receita e característica na ordem certa: "Poção de Feiticeiro dos Segredos".
- Mais de mil textos que estavam escritos direto no código em inglês agora são traduzíveis: telas
  (Introspecção, receita, contrato, Adoração ao Diabo...), mensagens de habilidade, adivinhações,
  comandos e guia.
- Livros de ritual da mesa de ritual em português, com o nome honorífico traduzido. Na mesa, o
  nome honorífico pode ser escrito em português ou em inglês (sem precisar acertar os acentos).
- Textos que o jogo compara com o que você digita continuam em inglês, porque o código só os
  entende assim: `accept`/`decline` dos contratos, a sintaxe `if ... then ...` da Escrita de
  Histórias, as palavras exigidas no nome honorífico de jogador e os cânticos do Castelo Sefirah.
- Avisos curtos aparecem em cima da barra de itens (barra de ação) em vez do chat. Ficam no chat
  só os textos para ler com calma: pedidos de contrato, falas de NPC, resultados com coordenadas,
  alvos de missão, preces, pedidos que precisam de resposta e respostas de comando.
- Conquistas e títulos de área (Submundo, Castelo Sefirah, Cidade Vitoriana...) em português.
- Em servidor dedicado, as mensagens montadas no servidor que não passam por chave de tradução
  aparecem em inglês; em mundo de um jogador tudo aparece em português.

## Cartas da Blasfêmia

- Feitas na bancada com as 9 receitas de poção de um Caminho (Sequências 9 a 1), em qualquer ordem.
  A Carta do Sol pede as receitas das Sequências 9 a 6 e as Fórmulas Completas das Sequências 5 a 1.
- Só existe uma Carta de cada Caminho por mundo: uma segunda some (aviso na barra de ação). Se a
  verdadeira for destruída (explosão, lava, cacto, cair no vazio), pode ser feita de novo.
- Clique direito mostra a receita da Sequência escolhida (e o ritual, se ela tiver um); agachado +
  clique direito troca a Sequência. No Caldeirão de Preparo serve como receita de qualquer
  Sequência do Caminho.
- Caminhos com Carta: Tolo, Porta, Erro, Sol, Tirano, Escuridão, Morte, Demônia, Sacerdote Vermelho,
  Mãe, Abismo, Visionário, Roda da Fortuna e Ministro.

## Rituais de avanço: como funcionam

Os pergaminhos, as Fórmulas Completas e as Cartas mostram só o ritual da novel e uma pista curta: descobrir como
fazê-lo faz parte do jogo. Este LEIAME é o guia com spoilers. Muitos rituais aceitam mais de um jeito, inclusive
poderes de outros Caminhos e Artefatos Selados.

## Rituais de avanço do Sol

Sem o ritual, o avanço falha e você perde o controle (regra de jogo `applyRituals`). Para o ritual
valer, é preciso ter no inventário, na hora de beber a poção, o pergaminho daquele ritual ou a Carta
da Blasfêmia do Sol com aquela Sequência escolhida.

- Sequência 5, Sacerdote da Luz: beber a poção em escuridão total, enterrado numa cápsula de gelo
  compactado ou gelo azul (10 blocos em volta do corpo). Também vale estar congelado por inteiro (neve fina,
  poderes de gelo) e/ou com os olhos tomados pela escuridão (Cegueira ou Escuridão).
- Sequência 4, Sem Sombra: agachado, usar um frasco de vidro vazio para guardar as próprias emoções
  (Frasco de Emoções) e beber a poção com ele no inventário.
- Sequência 3, Mentor da Justiça: escrever e assinar a sua justiça num livro, jurar por ela e
  mantê-la por 3 dias do Minecraft.
- Sequência 2, Buscador da Luz: ligar a memória da sua justiça a um item de Anjo do Sol e deixá-lo
  à sua frente ao beber.
- Sequência 1, Anjo Branco: reunir 100 fiéis (99 colonos e você) com o Registro Sagrado, feito na
  bancada. Qualquer Beyonder de Sequência 3 ou maior pode reunir fiéis, e cada colono segue uma fé
  só. A contagem "Fiéis: X/100" só aparece para o Sol na Sequência 2.
- Âncoras: os fiéis seguram a sanidade de Beyonders de Sequência 3 ou maior. O número de âncoras
  fica na aba **Âncoras** da Introspecção (só para Sequência 3 ou maior).

Os pergaminhos de ritual (roxos) mostram só o ritual. Eles saem, raramente, nos baús junto da
receita da mesma Sequência.

**Fórmula Completa:** receita da poção + pergaminho do ritual da mesma Sequência, na bancada.
Mostra a receita e o ritual lado a lado, serve de receita no Caldeirão de Preparo e vale como o
pergaminho na hora do ritual.

## Rituais de avanço da Porta

Valem as mesmas regras dos rituais do Sol: pergaminho, Fórmula Completa ou Carta da Porta na Sequência
certa, e sem o ritual o avanço falha. A Carta da Porta pede as receitas das Sequências 9 a 6 e as Fórmulas
Completas das Sequências 5 a 1.

- Sequência 5, Viajante: colocar 4 Magnetitas no Mundo Espiritual, cada uma a pelo menos 1.000 blocos
  das outras. Elas precisam continuar lá na hora de beber.
- Sequência 4, Feiticeiro dos Segredos: selar para sempre um Beyonder hostil de Sequência 4 ou 3, preso
  por qualquer poder que o deixe sem andar (Aprisionar, Confinamento, jaulas, Petrificação, Selamento, cabelos,
  plantas, aço, Conquista...), seu, de outro Caminho ou de um Artefato Selado. Pela regra do mod, só um poder de nível semideus alcança um semideus: na prática, um
  Artefato Selado feito com Característica de Sequência 4 ou maior. Se outro jogador ferir a criatura,
  o selo não vale. A criatura selada fica presa por correntes na cor da Porta, com o efeito do Selamento
  e estrelas em volta. Isso só acontece no ritual.
- Sequência 3, Errante: dentro de 3 construções diferentes do Mundo Espiritual, agachado, usar um livro
  e pena para registrá-las. Beber com o livro no inventário.
- Sequência 2, Andarilho dos Planos: deixar 9 lendas (livros assinados por você em atris) no Espaço
  Profundo, cada uma a pelo menos 500 blocos das outras.
- Sequência 1, Chave das Estrelas: o Espaço Profundo ganhou planetas pesados de pedra-negra, com
  rachaduras de magma, anel de ametista, luzes que giram em volta e feixes de estrelas nos polos. Pousar num deles, colocar uma Magnetita e ficar em pé nele por 5 minutos.
  Os planetas só aparecem em partes do Espaço Profundo que ainda não tinham sido geradas.

## Rituais de avanço do Tolo

Mesmas regras dos outros: pergaminho, Fórmula Completa ou Carta do Tolo na Sequência certa. A Carta do
Tolo pede as receitas das Sequências 9 a 6 e as Fórmulas Completas das Sequências 5 a 1.

- Sequência 5, Marionetista: beber a poção até 1 minuto depois de ouvir a Canção da Sereia (Tirano 5)
  a até 20 blocos, cantada por outro jogador ou por um Artefato Selado.
- Sequência 4, Feiticeiro Bizarro: dar o golpe final num Beyonder de Sequência 4 ou 3 diante de uma
  plateia de 30 pontos a até 32 blocos (colono 1, jogador 5, jogador do Visionário 15) e beber em até
  1 minuto.
- Sequência 3, Erudito de Outrora: ficar 7 dias seguidos do Minecraft no Vazio Histórico (sair antes,
  sair do jogo ou morrer zera).
- Sequência 2, Invocador de Milagres: marcar uma criatura com a Invocação do Vazio Histórico e, depois
  que ela morrer, invocá-la: ela volta ao presente e fica de vez. Ou reativar uma colônia abandonada (as que o
  MineColonies gera no mundo).
- Sequência 1, Atendente de Mistérios: 50 marionetes suas, cada uma com nome, vivendo a até 48 blocos de
  você ou dentro da sua colônia (soltas ou como colonos) por 7 dias do Minecraft (se ficarem menos de 50, recomeça).
  Marionetes-colono só contam se viverem de verdade na cidade: com uma casa, ou com uma cama onde dormiram nos
  últimos 2 dias (pode ser na pousada). Emprego não é preciso.

## Rituais de avanço do Erro

Mesmas regras dos outros: pergaminho, Fórmula Completa ou Carta do Erro na Sequência certa. A Carta do
Erro pede as receitas das Sequências 9 a 6 e as Fórmulas Completas das Sequências 5 a 1.

O Erro não precisa ter colônia: ele faz os rituais na colônia dos outros, mesmo sem permissão (é uma falha, um
bug, que não segue as regras). A proteção do MineColonies não o impede de clicar nos colonos, pendurar molduras
e pôr relógios nelas para esses rituais.
Ao se tornar Deus (Erro, Sequência 0), ele ignora toda a proteção de qualquer colônia: quebrar e pôr blocos,
abrir baús e prédios, mexer nos colonos.

- Sequência 5, Ladrão de Sonhos: entrar no sonho de 30 pessoas (colonos, aldeões, Beyonders Errantes ou
  jogadores) e beber com elas dormindo a até 48 blocos. Entra-se agachado e de mão vazia em quem dorme, ou
  usando um poder de sonho de qualquer Caminho (Pesadelo, Travessia de Sonhos, Tecelagem de Sonhos, Labirinto
  de Sonhos, Poema da Meia-Noite) perto de quem dorme. Vale dormir na cama ou adormecido por um poder.
- Sequência 4, Parasita: agachado e de mão vazia, pedir a 9 pessoas que deem tudo o que têm (inventário e
  equipamento passam para você). Aceitam: colonos felizes (7 ou mais), aldeões que gostam muito de você
  (reputação alta, Herói da Vila) e qualquer um que já seja seu aliado (convencido pela Eloquência, enganado...).
  Ferir um deles desfaz a oferta; quem morre deixa de contar. Beber com os 9 vivos.
- Sequência 3, Mentor do Engano: sem ferir nenhuma criatura por 1 dia, derrubar a ordem de uma região e beber
  dentro dela: uma colônia com pelo menos 10 colonos e felicidade geral 3 ou menos, ou uma vila comum com pelo
  menos 10 aldeões em que quase todos (80%) ficaram sem cama e sem posto de trabalho.
- Sequência 2, Cavalo de Troia do Destino: sem nenhum poder Beyonder. Renomear uma etiqueta na bigorna com o nome
  exato de um colono, aldeão ou Beyonder Errante e clicar nele com ela. Ele sai de cena: o colono fica "viajando"
  (continua na lista da colônia, com casa e emprego, só sem corpo); aldeão e Beyonder Errante ficam guardados. Você
  recebe as **Roupas** dele: usar veste (você fica com a aparência dele, para todos), usar de novo tira. Vestido,
  viva 6 dias entre os dele (dentro da colônia, ou a até 48 blocos de onde ele dormia) sem usar poderes. Enquanto
  for ele, você não fala no chat, some da lista do Tab e as mensagens do servidor com o seu nome não aparecem. Quem
  clicar em você vê a janela do colono de verdade (nome, emprego, felicidade); disfarçado de aldeão, você "balança a
  cabeça" como um aldeão sem trabalho.
  **Disfarce fora do ritual:** qualquer Beyonder do Erro, de qualquer Sequência, pode fazer o mesmo só como disfarce
  (sem a contagem dos 6 dias, que é só do Erro 3). Disfarçado de um colono, na colônia dele os guardas não te atacam
  (mesmo marcado como hostil) e você pode abrir baús e outros containers; quebrar, pôr blocos e mexer no resto
  continua bloqueado. Tirar as roupas ou usar um poder entre os dele desfaz o disfarce e o colono volta.
  **Parasitismo no modo Controlar** (ou qualquer controle de corpo) num colono: enquanto o corpo dele está com você,
  ele fica "viajando" (o MineColonies não cria outro igual) e, na colônia dele, você tem todas as permissões e os
  guardas não te atacam. Ao soltar o corpo, ele volta e tudo volta ao normal.
  Tirar as roupas (ou perdê-las), não vesti-las em 1 minuto ou usar um poder entre os dele estraga o ritual: ele
  volta para a vida dele e é preciso começar de novo com outra pessoa. Ao avançar, ele também volta.
- Sequência 1, Verme do Tempo: 12 relógios em molduras numa cidade: colônia com pelo menos 20 colonos ou vila comum
  com pelo menos 20 aldeões. O tempo da cidade se embaralha (moradores lentos ou rápidos ao acaso). Precisa durar 7 dias com você por perto; se faltar um
  relógio, recomeça.

## Rituais de avanço do Sacerdote Vermelho (Caçador)

Mesmas regras dos outros: pergaminho, Fórmula Completa ou Carta do Sacerdote Vermelho na Sequência certa. A
Carta pede as receitas das Sequências 9 a 6 e as Fórmulas Completas das Sequências 5 a 1. Nenhum depende de um
poder do próprio Caminho, para servir também a quem chegar por outro Caminho.

- Sequência 5, Ceifador: beber a até 8 blocos de um Beyonder mais forte que você (Sequência menor), vivo e preso de
  qualquer jeito (sem conseguir andar, petrificado, conquistado, adormecido por um poder...).
- Sequência 4, Cavaleiro de Sangue de Ferro: beber com 30 companheiros fortes a até 64 blocos. Companheiros: guardas
  da sua colônia felizes (7 ou mais), aliados (qualquer um) ou subordinados seus. Fortes: armadura de ferro completa
  ou melhor, ou Beyonders.
- Sequência 3, Bispo da Guerra: vencer uma guerra: 20 invasores caindo perto de você (invasões do MineColonies,
  invasões de aldeia, saqueadores...), mortos por você, seus guardas ou aliados, até não sobrar nenhum por perto.
  Beber em até 1 dia do Minecraft.
- Sequência 2, Bruxo do Clima: mudar o clima (limpo para chuva/tempestade ou o contrário) até 10 segundos depois de
  usar um poder (seu, de outro Caminho ou de um Artefato Selado) ou um item, sem nenhum outro jogador a até 64 blocos.
  A troca natural e o comando `/weather` não contam. Beber em até 1 minuto.
- Sequência 1, Conquistador: conquistar colônias que somem pelo menos 100 colonos (a sua não conta) e beber.

**Conquista de colônias** (para quem está na Sequência 2 do Sacerdote Vermelho):
1. Atacar os defensores de uma colônia que não é sua declara guerra a ela: guardas do MineColonies e, nos Reinos de
   NPC, também os golens de ferro que guardam a prefeitura.
2. Em guerra, pode tudo dentro dessa colônia: quebrar e pôr blocos, abrir baús e prédios, pegar itens, atacar
   qualquer um. A guerra acaba quando você conquista a colônia ou quando morre.
3. Com nenhum defensor de pé a até 48 blocos da prefeitura, fincar um estandarte qualquer a até 16 blocos dela.
   Enquanto houver torres de guarda, a colônia contrata novos guardas para as vagas: derrubar as torres ajuda.
4. A colônia obedece (você vira oficial), reverencia (os colonos viram seus fiéis) e teme (tremem perto de você).
   Um jogador retoma a própria colônia matando o conquistador dentro dela.

**Reinos de NPC:** metade das colônias abandonadas que o MineColonies gera no mundo nasce como um reino habitado,
com nome ("Reino de ..."), prédios funcionando e de 10 a 24 moradores. Todo reino tem pelo menos 2 torres de
guarda, com guardas de espada, escudo e armadura de ferro, e golens de ferro guardando a prefeitura. A outra metade
continua abandonada (Invocador de Milagres, Tolo 2).

## Rituais de avanço da Demônia

Mesmas regras dos outros: pergaminho, Fórmula Completa ou Carta da Demônia na Sequência certa. A Carta pede as
receitas das Sequências 9 a 6 e as Fórmulas Completas das Sequências 5 a 1. Nenhum depende de um poder da própria
Demônia.

- Sequência 5, Demônia da Aflição: queimar sem parar por 3 minutos, de qualquer fonte de fogo (lava, fogueira,
  poderes, flechas de fogo...), sem Resistência ao Fogo nem imunidade. Se o fogo apagar, recomeça. Beber ainda em
  chamas, ou até 1 minuto depois, com a sanidade acima da metade.
- Sequência 4, Demônia do Desespero: deixar doentes 30 pessoas diferentes (colonos, aldeões, jogadores ou Beyonders
  Errantes) em até 3 dias. Contam veneno, murchar, fome, Praga Mental e Corrosão, de qualquer fonte sua: poções
  arremessadas, flechas, poderes de qualquer Caminho, Artefatos Selados. Quem adoeceu há mais de 3 dias deixa de
  contar.
- Sequência 3, Demônia da Perpetuidade: entrar no Mundo do Espelho. O seu reflexo aparece, com a sua aparência, a
  sua vida e o que você veste. Qualquer um destes serve:
  - escravizar: vencê-lo sozinha (ele não morre, se curva). Se outra criatura o ferir, ou você usar um Artefato
    Selado na mão, não vale;
  - apaixonar: usar nele um poder de encanto (Charme ou outro de qualquer Caminho) a até 16 blocos;
  - reconciliar: ficar a até 16 blocos dele, no Mundo do Espelho, por 1 dia do Minecraft sem nunca atacá-lo.
  Depois disso ele acompanha você. Beber quando quiser.
- Sequência 2, Demônia da Catástrofe: 40 mortes a até 128 blocos de você em 10 minutos, 5 delas suas (qualquer
  criatura, de qualquer jeito: explosões, raios, pragas, guerra...). Beber enquanto o desastre dura.
- Sequência 1, Demônia do Apocalipse: qualquer um destes:
  - fim de uma era: até 1 dia depois de o Dragão do Fim morrer (de quem for), ou no dia 100, 200, 300... do mundo;
  - apocalipse: durante um desastre como o da Sequência 2, ou até 10 minutos depois de alguém usar o poder
    Apocalipse a até 128 blocos;
  - queda de uma divindade: até 1 dia depois da morte de um ser de Sequência 1 ou 0 que você feriu.

## Rituais de avanço do Tirano

Mesmas regras dos outros: pergaminho, Fórmula Completa ou Carta do Tirano na Sequência certa. A Carta pede as
receitas das Sequências 9 a 6 e as Fórmulas Completas das Sequências 5 a 1. Nenhum depende de um poder do próprio
Tirano.

- Sequência 5, Cantor do Oceano: beber "na barriga" do mar, abafando a voz dele: dentro de um Monumento Oceânico, ou
  fechado numa cápsula de obsidiana (ou obsidiana chorona) em qualquer bioma de oceano, em cima, embaixo e dos lados,
  com até 4 blocos de espaço por dentro.
- Sequência 4, Enterrador do Cataclismo: nos últimos 60 segundos, a até 64 blocos, causar um terremoto e um tsunami
  e beber no meio deles. Terremoto: um poder de terremoto (de qualquer Caminho) ou 5 explosões suas (TNT, poderes,
  Artefatos Selados...). Tsunami: um poder de tsunami, dilúvio ou maré, ou 5 explosões suas debaixo d'água.
- Sequência 3, Rei do Mar: uma torre de pedra (pedra, pedregulho, tijolos de pedra, ardósia, prismarinho...) num
  oceano, do fundo do mar até acima da água (no máximo 3 blocos acima), com pelo menos 8 blocos dentro d'água, a até
  24 blocos de você. Em
  volta dela (96 blocos), nenhuma criatura forte viva (guardiões, afogados, Beyonders que não são colonos) e pelo
  menos 3 vencidas por você ali nos últimos 3 dias (um Guardião Ancião ou um Beyonder de Sequência 4 ou menor vale
  pelos 3).
- Sequência 2, Calamidade: atingir 20 pessoas (colonos, aldeões, jogadores ou Beyonders Errantes) com um desastre
  seu em até 10 minutos: raios, fogo, explosões, congelamento, afogamento, blocos caindo ou poderes de qualquer
  Caminho. Depois o desastre para: 1 minuto sem ninguém mais atingido. Então, em até 5 minutos, beber diante de pelo
  menos 10 delas (a até 48 blocos) e sem nenhum outro jogador a até 64 blocos.
- Sequência 1, Deus do Trovão: com raios seus (poderes de qualquer Caminho, Artefatos Selados, tridente com
  Canalização), atingir 3 Anjos de Sequência 2 que querem subir: Beyonders Errantes ou criaturas de Sequência 2, ou
  jogadores de Sequência 2 no meio do avanço. Um de Sequência 1 (ou menor) vale pelos 3.

## Ritual da mesa: Passagem para a Névoa da História

Ritual novo da mesa de ritual, exclusivo do Caminho do Vidente, a partir da Sequência 4. O livro
"Crônica Esquecida" aparece nos baús junto com os outros livros de ritual. Quem não é Vidente de
Sequência 4 ou maior falha o ritual, como em qualquer ritual errado.

- Vela cinza; sacrifícios: 2 livros escritos e 1 relógio.
- Nome honorífico (em português ou inglês): "Sombra das eras que já passaram", "Guardiã de toda
  história enterrada sob a névoa", "Abre para mim o caminho para a névoa da história".
- Abre um portal para o Vazio Histórico (a névoa da história). Feito lá dentro, abre o portal de volta
  para o Mundo de Cima: leve a mesa e os materiais. Para o Mundo Espiritual já existe a Chave do Viajante.

## Correções de bugs

Portadas da nossa versão (onde o criador ainda não tinha corrigido):

- Arquivos de dados que falhavam ao carregar; receitas de poção no servidor dedicado.
- Força do vento do Tirano que não acabava; voo da Manipulação do Vento que nunca funcionava.
- Estado misturado entre cliente e servidor e que vazava de um mundo para outro.
- Habilidades alternáveis que contavam o tempo duas vezes em mundo de um jogador.
- HUD de griefing e digestão que era sobrescrito a cada segundo.
- Pele de Aço, Escamas Ilusórias e o efeito Conquistado, que nunca funcionavam.
- Passivas que não atualizavam na hora ao trocar de Caminho ou Sequência; crash do Balancing.
- Tela de Introspecção que não cabia em resoluções menores.
- Cidade Vitoriana nascendo sobre rios e lagos.
- Fuga da Luz Inextinguível (Sol 0) que só subia um bloco.
- Seleção de sub-habilidade segura entre threads; itens com textura roxa e preta.
- Otimizações sem tirar mecânicas (rede, agendadores, chefes, renderização).

Achadas na 1.13.3:

- Crash do servidor ao causar dano numa entidade com sequência fora do normal.
- Resistência a Maldição, Resistência a Veneno e Autoridade da Malícia (Abismo) nunca eram ativadas.
- Jaula de Chamas não era limpa ao fechar o mundo.
- Nome honorífico do Gigante do Crepúsculo nunca era reconhecido (`twilight_light` em vez de `twilight_giant`).
- Ingredientes compartilhados por vários Caminhos nunca eram achados na busca por Caminho.
- Mixin de cliente carregado no servidor dedicado.

## Como foi testado

- Servidor dedicado: todas as habilidades de todos os Caminhos e Sequências (jogador e NPC) e todas
  as passivas foram usadas, sem nenhum erro.
- Cliente em português: telas, itens, mensagens e o guia conferidos por captura de tela.
