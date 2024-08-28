export interface Trade {
    accountid: number;
    created: Date;
    id: string;
    quantity: number;
    security: string;
    side: Side;
    state: State;
    updated: Date;
    unitPrice?: number;
}

export enum Side {
    Sell = 'Sell',
    Buy = 'Buy'
}

export enum State {
    New = 'New',
    Processing = 'Processing',
    Pending = 'Pending',
    Settled = 'Settled'
}

export interface Position {
    accountid: number;
    quantity: number;
    security: string;
    updated: Date;
    value: number;
    marketValue?: number;
}

export interface TradeTicket {
    side: 'Sell' | 'Buy';
    quantity: number;
    security: string;
    accountId: number;
    unitPrice?: number;
}

export interface StockPrice {
    price: number;
    ticker: string;
}
