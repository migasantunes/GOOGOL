(function(){
  
  // Função que recebe os dados de estatísticas e renderiza o HTML correspondente
  function renderStats(s){
    // Obter o elemento container das estatísticas
    const el = document.getElementById('stats');
    if(!el){ return; } // Se não existir, sair
    
    // Se não houver dados ou conexão, mostrar mensagem de espera
    if(!s){ 
      el.innerHTML = '<div style="text-align:center; padding:1rem; color:var(--text-muted);">Waiting for data stream...</div>'; 
      return; 
    }

    // 1. Renderizar Top Queries como "chips" ou "tags"
    // Mapeia cada query para um elemento visual com o texto e contador
    const qItems = (s.topQueries||[])
      .map(x => `
        <div class="stat-chip">
          <span class="query-text">${x.query}</span>
          <span class="query-count">${x.count}</span>
        </div>
      `).join('') || '<div style="color:var(--text-muted); font-style:italic;">No searches yet</div>';

    // 2. Renderizar tabela de Barrels (nós de armazenamento)
    // Cada barrel mostra: estado (online/offline), tamanho do índice, latência média
    const bRows = (s.barrels||[])
      .map(x => `
        <tr>
          <td>
            <div style="display:flex; align-items:center; gap:8px;">
              <!-- Indicador visual de estado: verde se ativo, vermelho se inativo -->
              <span class="status-dot ${x.active ? 'online' : 'offline'}"></span>
              <strong>${x.label}</strong>
            </div>
          </td>
          <td style="font-family:'Fira Code', monospace;">${x.indexSize.toLocaleString()}</td>
          <td style="color:var(--text-muted);">${(x.avgResponseDeci||0)}ds</td>
        </tr>
      `).join('') || '<tr><td colspan="3" style="text-align:center; padding:1rem;">No barrels registered</td></tr>';

    // Montar o HTML final com grid de estatísticas
    el.innerHTML = `
      <div class="stats-grid">
        <!-- Secção de pesquisas mais populares -->
        <section>
          <h3 class="stats-heading">Top Searches</h3>
          <div class="chips-container">${qItems}</div>
        </section>

        <!-- Secção de nós de armazenamento (barrels) -->
        <section>
          <h3 class="stats-heading">Storage Nodes (Barrels)</h3>
          <div class="table-responsive">
            <table class="modern-table">
              <thead>
                <tr>
                  <th>Node Status</th>
                  <th>URLs Indexed</th>
                  <th>Avg Latency</th>
                </tr>
              </thead>
              <tbody>${bRows}</tbody>
            </table>
          </div>
        </section>
      </div>`;
  }

  // Configuração da conexão WebSocket para receber estatísticas em tempo real
  try {
    // Criar conexão SockJS (fallback para navegadores sem WebSocket nativo)
    const sock = new SockJS('/ws');
    
    // Criar cliente STOMP sobre a conexão SockJS
    const client = Stomp.over(sock);
    
    // Desativar logs de debug na console para manter limpo
    client.debug = ()=>{};
    
    // Conectar ao servidor WebSocket
    client.connect({}, ()=>{
      // Após conectar, subscrever ao tópico de estatísticas
      // Quando receber mensagem, parse JSON e renderizar
      client.subscribe('/topic/stats', msg => {
        try{ 
          renderStats(JSON.parse(msg.body)); 
        }catch(e){ 
          console.error(e); 
        }
      });
    });
  } catch(e) {
    // Se WebSocket não estiver disponível, apenas logar (não é crítico)
    console.log("WebSocket not available");
  }
})();