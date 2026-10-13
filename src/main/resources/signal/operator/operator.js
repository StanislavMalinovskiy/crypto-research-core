'use strict';
// Presentation only: exact server strings are never converted to financial numbers.
const labels = {
  monitor: 'Сохранённые факты сессии', diagnostic: 'Диагностика', scope: 'Точно запрошенный диапазон',
  effectiveStatus: 'Основной результат (effectiveStatus)', effectiveReasons: 'Причины основного результата',
  pureStatus: 'Отдельный результат чистого правила (pureStatus)', pureReasons: 'Причины чистого правила',
  observedAt: 'Наблюдение панели, UTC', R: 'R: чтение БД, UTC', D: 'D: после расчёта, UTC', K: 'K=D, UTC', E: 'E: конец событийного окна, UTC',
  recordedState: 'Записанное состояние, не здоровье процесса', reason: 'Исходный код причины',
  counters: 'Локальные счётчики / резервы, не счета', requests: 'Начатые запросы', receivedBytes: 'Полученные байты',
  subscriptionBytes: 'Байты подписок', retainedBytes: 'Сохранённые байты', rpcHundredths: 'Локальные RPC units ×100',
  reservedHundredths: 'Резерв units ×100', transactions: 'Транзакции', states: 'Состояния', duplicates: 'Повторы', conflicts: 'Конфликты', failures: 'Ошибки',
  dispositions: 'Все группы disposition', positions: 'Исходные committed positions', unresolvedCounts: 'Все группы незавершённых gaps',
  totalUnresolved: 'Полное число незавершённых gaps', detailsTruncated: 'Детали ограничены исходными первыми 100', intervals: 'Исходные детали gaps',
  candles: 'Все выбранные свечи', prior: 'Предыдущая свеча', current: 'Текущая свеча', reconciliation: 'Сверка выбранных записей',
  baseAmount: 'Точные base raw units', poolNetQuoteLamports: 'Точные pool-net lamports', ohlc: 'Точные OHLC отношения',
  numerator: 'Числитель', denominator: 'Знаменатель', operationIdentities: 'Исходные operation identities',
  distinctSignatures: 'Scoped signatures с квалификацией', sourceProofBytes: 'Scoped source/proof bytes с квалификацией',
  qualification: 'Квалификация наблюдения', limitations: 'Ограничения', sourceLimitations: 'Ограничения source', candleLimitations: 'Ограничения свечей',
  processActivity: 'Процесс: не проверен', transportActivity: 'Транспорт: не проверен', coverage: 'Покрытие: не доказано',
  unreceivedLoss: 'Неполученные данные', memoryQueueOccupancy: 'Очередь памяти: неизвестна', modelCost: 'MODEL_COST: неизвестна', actualInvoice: 'ACTUAL_INVOICE: неизвестен'
};
function render(value, target) {
  if (value === null || typeof value !== 'object') {
    const text = document.createElement('span');
    text.textContent = value === null ? 'null — отсутствует / не наблюдалось' : String(value);
    target.appendChild(text);
    return;
  }
  const table = document.createElement('table');
  for (const [key, item] of Object.entries(value)) {
    const row = document.createElement('tr');
    const heading = document.createElement('th');
    heading.scope = 'row'; heading.textContent = labels[key] || key;
    const cell = document.createElement('td'); render(item, cell);
    row.appendChild(heading); row.appendChild(cell); table.appendChild(row);
  }
  target.appendChild(table);
}
function manualPanel(name, route, method) {
  const form = document.getElementById(name + '-form');
  const status = document.getElementById(name + '-status');
  const output = document.getElementById(name + '-output');
  const button = form.querySelector('button');
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (button.disabled) return;
    button.disabled = true;
    output.replaceChildren();
    status.textContent = 'Ручной запрос выполняется; прежний результат не обозначается свежим.';
    const observedByBrowser = new Date().toISOString();
    try {
      const parameters = new URLSearchParams();
      for (const [key, value] of new FormData(form).entries()) parameters.append(key, value);
      const response = await fetch(route + '?' + parameters.toString(), {method, headers: {'X-Operator-Request': 'local-manual-v1'}, cache: 'no-store', credentials: 'omit', redirect: 'error'});
      const data = await response.json();
      render(data, output);
      const primary = data.diagnostic ? data.diagnostic.effectiveStatus : data.status;
      status.textContent = (response.ok ? 'Результат: ' : 'Явное отсутствие / отказ: ') + primary + '. Начало последнего ручного запроса браузера: ' + observedByBrowser + ' (не source clock). Панель не обновляется автоматически; возраст оценивайте по исходным UTC наблюдениям.';
    } catch (failure) {
      output.replaceChildren();
      status.textContent = 'READ_UNAVAILABLE: запрос не завершился. Нет свежего результата, fallback или автоматического повтора. Начало запроса браузера: ' + observedByBrowser;
    } finally { button.disabled = false; }
  });
}
manualPanel('monitor', '/operator/api/capture', 'GET');
manualPanel('diagnostic', '/operator/api/diagnostic', 'POST');
