// Script para tornar as ações de indexação assíncronas (AJAX) evitando reload da página
(function() {
  
  // Função que adiciona comportamento AJAX a formulários com um dado seletor CSS
  function wireAjax(selector) {
    // Selecionar todos os formulários que correspondem ao seletor
    document.querySelectorAll(selector).forEach(form => {
      
      // Adicionar listener de submissão a cada formulário
      form.addEventListener('submit', async (e) => {
        // Prevenir submissão tradicional do formulário
        e.preventDefault();
                const btn = form.querySelector('button[type="submit"]');
        if (!btn) return;
        
        // Guardar texto original do botão para restaurar depois
        const original = btn.textContent;
        
        // Desativar botão e mostrar estado de carregamento
        btn.disabled = true; 
        btn.textContent = 'Indexing...';
        
        try {
          // Recolher dados do formulário
          const data = new FormData(form);
          
          // Criar controlador para poder abortar o pedido se demorar muito
          const ctl = new AbortController();
                    const timer = setTimeout(() => ctl.abort(), 4000);
          
          // Fazer pedido POST assíncrono para a ação do formulário
          const resp = await fetch(form.action, { 
            method: 'POST', 
            body: data, 
            signal: ctl.signal, // Associar o sinal de abort
            credentials: 'same-origin' // Incluir cookies da sessão
          });
          
          // Limpar o timeout se o pedido completou a tempo
          clearTimeout(timer);
          
          // Atualizar texto do botão conforme resultado
          btn.textContent = resp && resp.ok ? 'Queued ✔' : 'Failed';
          
        } catch (err) {
                    btn.textContent = 'Failed';
        } finally {
          // Após 1.5 segundos, restaurar o botão ao estado original
          setTimeout(() => { 
            btn.disabled = false; 
            btn.textContent = original; 
          }, 1500);
        }
      });
    });
  }

  // Executar quando o DOM estiver completamente carregado
  document.addEventListener('DOMContentLoaded', () => {
    
    // Aplicar comportamento AJAX a todos os formulários de indexação individual
    wireAjax('form.ajax-index-one');
    
    // Configurar o botão "Index All" que indexa todas as histórias de uma vez
    const allBtn = document.getElementById('index-all-btn');
    if (allBtn) {
      allBtn.addEventListener('click', () => {
        // Guardar texto original
        const original = allBtn.textContent;
        
        // Desativar e mostrar estado de carregamento
        allBtn.disabled = true; 
        allBtn.textContent = 'Indexing...';
        
        // Obter todos os formulários de indexação individual
        const itemForms = Array.from(document.querySelectorAll('form.ajax-index-one'));
        
        // Disparar evento de submit em cada formulário (simula clique em cada botão)
        itemForms.forEach(f => {
          const event = new Event('submit', { bubbles: false, cancelable: true });
          f.dispatchEvent(event);
        });
        
        // Após 0.8 segundos, mostrar mensagem de sucesso
        setTimeout(() => { 
          allBtn.disabled = false; 
          allBtn.textContent = 'Queued ✔'; 
        }, 800);
        
        // Após 2 segundos, restaurar texto original
        setTimeout(() => { 
          allBtn.textContent = original; 
        }, 2000);
      });
    }
  });
})();
