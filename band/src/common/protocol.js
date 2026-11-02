// Every refresh is a transaction. Only a complete, validated snapshot can replace the cache.
function validQuote(q) {
  if (!q || typeof q.symbol !== 'string' || q.symbol.length > 24 || typeof q.name !== 'string' || q.name.length > 30) return false
  if (typeof q.region !== 'string' || q.region.length > 10 || typeof q.time !== 'number' || q.time < 0 || !isFinite(q.time)) return false
  if (!Array.isArray(q.trend) || q.trend.length > 20 || q.trend.some(function (v) { return typeof v !== 'number' || !isFinite(v) || v < 0 || v > 100 })) return false
  if (q.history !== undefined) {
    if (!Array.isArray(q.history) || q.history.length > 5) return false
    var lastDate = '', lastTime = 0
    for (var i = 0; i < q.history.length; i++) {
      var day = q.history[i]
      if (!day || typeof day.date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(day.date) || day.date <= lastDate || typeof day.time !== 'number' || !isFinite(day.time) || day.time <= lastTime || typeof day.close !== 'number' || !isFinite(day.close) || day.close <= 0) return false
      lastDate = day.date; lastTime = day.time
    }
  }
  if (q.time > 0) {
    if (['value', 'previous', 'change', 'percent'].some(function (k) { return typeof q[k] !== 'number' || !isFinite(q[k]) })) return false
    if (q.value <= 0 || q.previous <= 0) return false
    if (Math.abs(q.change - (q.value - q.previous)) > 0.04) return false
    if (Math.abs(q.percent - ((q.value - q.previous) / q.previous * 100)) > 0.04) return false
  }
  return true
}
function validSnapshot(s) {
  if (!s || (s.v !== 1 && s.v !== 2) || !/^\d{1,16}$/.test(s.id) || !Array.isArray(s.quotes) || s.quotes.length < 1 || s.quotes.length > 40 || typeof s.generatedAt !== 'number' || !isFinite(s.generatedAt) || s.generatedAt <= 0) return false
  var seen = {}
  return s.quotes.every(function (q) { if (!validQuote(q) || seen[q.symbol]) return false; seen[q.symbol] = true; return true })
}
function createReceiver(saved) {
  var current = validSnapshot(saved) ? saved : null
  var pending = null
  return {
    accept: function (s) { if (validSnapshot(s)) current = s },
    receive: function (packet, now) {
      var p
      try { p = typeof packet === 'string' ? JSON.parse(packet) : packet } catch (e) { return null }
      if (!p || p.v !== 2 || !/^\d{1,16}$/.test(p.id)) return null
      if (p.type === 'commit' && current && p.id === current.id) return { ack: { type: 'ack', v: 2, id: p.id, count: current.quotes.length } }
      if (current && Number(p.id) <= Number(current.id)) return null
      if (p.type === 'begin') {
        if (typeof p.count !== 'number' || p.count % 1 || p.count < 1 || p.count > 40 || typeof p.generatedAt !== 'number' || !isFinite(p.generatedAt) || p.generatedAt <= 0 || p.generatedAt > now + 300000) return null
        pending = { id: p.id, count: p.count, generatedAt: p.generatedAt, redUp: p.redUp !== false, started: now, quotes: [] }
        return { progress: 0 }
      }
      if (!pending || pending.id !== p.id || now - pending.started > 60000) return null
      if (p.type === 'quote') {
        if (typeof p.index !== 'number' || p.index % 1 || p.index < 0 || p.index >= pending.count || !validQuote(p.quote)) return null
        pending.quotes[p.index] = p.quote
        return { progress: pending.quotes.filter(function (q) { return !!q }).length, count: pending.count }
      }
      if (p.type === 'commit') {
        var s = { v: 2, id: pending.id, generatedAt: pending.generatedAt, redUp: pending.redUp, quotes: [] }
        for (var i = 0; i < pending.count; i++) { if (!pending.quotes[i]) return { nack: true, id: p.id }; s.quotes.push(pending.quotes[i]) }
        pending = null
        return validSnapshot(s) ? { snapshot: s } : { nack: true, id: p.id }
      }
      return null
    }
  }
}
module.exports = { validQuote: validQuote, validSnapshot: validSnapshot, createReceiver: createReceiver }
