function filter(event) {
    if (event['@fields']) {
        for (p in event['@fields']) {
            event[p] = event['@fields'][p];
        }
    }
    return event;
}