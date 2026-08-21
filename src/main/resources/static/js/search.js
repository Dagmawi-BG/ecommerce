// Storefront search autocomplete: debounced calls to /api/products/autocomplete
// populate the <datalist> as the shopper types.
(function () {
    var input = document.getElementById('product-search');
    var list = document.getElementById('product-suggestions');
    if (!input || !list) {
        return;
    }

    var timer = null;

    input.addEventListener('input', function () {
        var q = input.value.trim();
        clearTimeout(timer);
        if (q.length < 2) {
            list.innerHTML = '';
            return;
        }
        timer = setTimeout(function () {
            fetch('/api/products/autocomplete?q=' + encodeURIComponent(q))
                .then(function (r) { return r.ok ? r.json() : []; })
                .then(function (items) {
                    list.innerHTML = items.map(function (i) {
                        var name = String(i.name).replace(/"/g, '&quot;');
                        return '<option value="' + name + '"></option>';
                    }).join('');
                })
                .catch(function () { /* ignore transient errors */ });
        }, 150);
    });
})();
