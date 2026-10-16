export const config = {
  baseUrl: __ENV.BASE_URL || 'http://localhost:8082',
  targetConcertId: 'a1111111-1111-1111-1111-111111111111',
  ticketTypes: [
    {
      id: 'c1111111-1111-1111-1111-111111111111',
      name: 'Vé SVIP President',
      price: 4500000,
    },
    {
      id: 'c2222222-2222-2222-2222-222222222222',
      name: 'Vé VIP Floor',
      price: 2500000,
    },
    {
      id: 'c3333333-3333-3333-3333-333333333333',
      name: 'Vé GA Standing',
      price: 1200000,
    },
  ],
  defaultHeaders: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
};
