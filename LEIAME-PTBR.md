# LOTM PTBR — LOTMCraft 1.13.3 em português

Versão pessoal do LOTMCraft 1.13.3, traduzida para o português do Brasil e com correções de bugs.
Por dentro o mod continua sendo `lotmcraft`, então mundos da 1.13.3 abrem normalmente. Na lista de
mods ele aparece como **LOTM PTBR**.

## Instalação

1. No CurseForge, crie um perfil **Minecraft 1.21.1** com **NeoForge 21.1.217** ou mais novo.
2. Abra a pasta do perfil (três pontinhos → *Open Folder*) e coloque o `LOTM-PTBR-1.13.3.jar` na pasta `mods`.
3. Se o LOTMCraft original estiver lá, tire-o: os dois não podem ficar juntos.
4. No jogo, escolha *Português (Brasil)* em Opções → Idioma.

## Tradução

- Todos os textos do arquivo de idioma (`pt_br.json`), com os nomes da nossa versão:
  Ministro (Justiciar), Feiticeiro Bizarro, Feiticeiro dos Segredos etc.
- Nomes de poção, receita e característica na ordem certa: "Poção de Feiticeiro dos Segredos".
- Mais de mil textos que estavam escritos direto no código em inglês agora são traduzíveis: telas
  (Introspecção, receita, contrato, Adoração ao Diabo...), mensagens de habilidade, adivinhações,
  comandos e guia.
- Textos que o jogo compara com o que você digita continuam em inglês, porque o código só os
  entende assim: `accept`/`decline` dos contratos, a sintaxe `if ... then ...` da Escrita de
  Histórias, as palavras exigidas no nome honorífico e os cânticos do Castelo Sefirah.
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

## Rituais de avanço do Sol

Sem o ritual, o avanço falha e você perde o controle (regra de jogo `applyRituals`). Para o ritual
valer, é preciso ter no inventário, na hora de beber a poção, o pergaminho daquele ritual ou a Carta
da Blasfêmia do Sol com aquela Sequência escolhida.

- Sequência 5, Sacerdote da Luz: beber a poção em escuridão total, enterrado numa cápsula de gelo
  compactado ou gelo azul (10 blocos em volta do corpo).
- Sequência 4, Sem Sombra: agachado, usar um frasco de vidro vazio para guardar as próprias emoções
  (Frasco de Emoções) e beber a poção com ele no inventário.
- Sequência 3, Mentor da Justiça: escrever e assinar a sua justiça num livro, jurar por ela e
  mantê-la por 3 dias do Minecraft.
- Sequência 2, Buscador da Luz: ligar a memória da sua justiça a um item de Anjo do Sol e deixá-lo
  à sua frente ao beber.
- Sequência 1, Anjo Branco: reunir 100 fiéis (99 aldeões e você) com o Registro Sagrado, feito na
  bancada. Qualquer Beyonder de Sequência 3 ou maior pode reunir fiéis, e cada aldeão segue uma fé
  só. A contagem "Fiéis: X/100" só aparece para o Sol na Sequência 2.
- Âncoras: os fiéis seguram a sanidade de Beyonders de Sequência 3 ou maior. O número de âncoras
  fica na aba **Âncoras** da Introspecção (só para Sequência 3 ou maior).

Os pergaminhos de ritual (roxos) mostram só o ritual. Eles saem, raramente, nos baús junto da
receita da mesma Sequência.

**Fórmula Completa:** receita da poção + pergaminho do ritual da mesma Sequência, na bancada.
Mostra a receita e o ritual lado a lado, serve de receita no Caldeirão de Preparo e vale como o
pergaminho na hora do ritual.

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
