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
