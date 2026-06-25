// IIFE (Immediately Invoked Function Expression) para encapsular o código e evitar poluir o escopo global
(function(){
  // Obter referências aos elementos do DOM necessários para o modal de submissão de URL
  const openBtn = document.getElementById('open-submit-url');
  const modal = document.getElementById('submit-url-modal');
  const closeBtn = document.getElementById('close-submit-url');
  const form = document.getElementById('submit-url-form');
  const toast = document.getElementById('toast');
  
  // Se algum elemento não existir, sair da função (página pode não ter o modal)
  if (!openBtn || !modal || !closeBtn || !form || !toast) return;

    function open(){ modal.hidden = false; }
    function close(){ modal.hidden = true; }
  
  // Função para mostrar notificações temporárias (toast) ao utilizador
  // Recebe o texto a mostrar e o tipo (success/error) para estilização
  function showToast(text, type){
    toast.textContent = text;
    toast.className = type ? ' ' + type : '';
    toast.hidden = false;
    // Esconde automaticamente após 2.5 segundos
    setTimeout(()=>{ toast.hidden = true; }, 2500);
  }

  // Event listener para abrir o modal quando o botão é clicado
  openBtn.addEventListener('click', open);
    closeBtn.addEventListener('click', close);
    modal.addEventListener('click', (e)=>{ if(e.target===modal) close(); });

  // Event listener para submissão do formulário de URL
  form.addEventListener('submit', async (e)=>{
    // Prevenir comportamento padrão do formulário (reload da página)
    e.preventDefault();
    
    // Recolher dados do formulário
    const data = new FormData(form);
    
    try {
      // Enviar URL para o servidor via POST assíncrono
      const res = await fetch('/submit', { method:'POST', body: data });
      
      // Verificar se a resposta foi bem sucedida
      if (res.ok) { 
        showToast('Queued ✔', 'success'); // Mostrar mensagem de sucesso
        form.reset(); // Limpar o formulário
        close(); // Fechar o modal
      }
      else { 
        showToast('Failed to enqueue', 'error'); // Mostrar erro se falhou
      }
    } catch(err){ 
      // Capturar erros de rede ou outros
      showToast('Error submitting URL', 'error'); 
    }
  });
})();
