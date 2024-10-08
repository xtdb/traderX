import { ColDef, GridApi, GridReadyEvent } from 'ag-grid-community';
import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { Account } from 'main/app/model/account.model';
import { PositionService } from 'main/app/service/position.service';
import { Observable } from 'rxjs';
import { Trade } from '../../model/trade.model';
import { TradeFeedService } from 'main/app/service/trade-feed.service';

@Component({
    selector: 'app-trade-blotter',
    templateUrl: 'trade-blotter.component.html'
})
export class TradeBlotterComponent implements OnChanges, OnDestroy {
    trades$: Observable<Trade[]>;
    @Input() account?: Account;
    trades: Trade[] = [];
    gridApi: GridApi;
    pendingTrades: Trade[] = [];
    isPending = true;
    socketUnSubscribeFn: Function;
    width = '100%';
    height = '350px';
    columnDefs: ColDef[] = [
        {
            headerName: 'SECURITY',
            field: 'security',
	    width: 50
        },
        {
            headerName: 'QUANTITY',
            field: 'quantity',
	    width: 50
        },
        {
            headerName: 'SIDE',
            field: 'side',
	    width: 50
        },
        {
            headerName: 'STATE',
            field: 'state',
            enableCellChangeFlash: true,
	    width: 50
        },
        {
            headerName: 'UNIT PRICE',
            field: 'unitPrice',
	    width: 50
        }
    ];

    constructor(private tradeFeed: TradeFeedService, private tradeService: PositionService) { }

    ngOnChanges(change: SimpleChanges) {
        if (change.account?.currentValue && change.account.currentValue !== change.account.previousValue) {
            const accountId = change.account.currentValue.id;
            this.isPending = true;
            this.tradeService.getTrades(accountId).subscribe((trades: Trade[]) => {
                this.trades = trades;
                this.processPendingTrades();
            });
            this.socketUnSubscribeFn?.();
            this.socketUnSubscribeFn = this.tradeFeed.subscribe(`/accounts/${accountId}/trades`, (data: Trade) => {
                console.log('Trade blotter feed...', data);
                this.updateTrades(data);
            });
        }
    }

    onGridReady(params: GridReadyEvent) {
        console.log('trade blotter is ready...');
        this.gridApi = params.api;
        this.gridApi.sizeColumnsToFit();
    }

    getRowNodeId(data: Trade) {
        return data.id;
    }

    ngOnDestroy() {
        this.socketUnSubscribeFn?.();
    }

    private processPendingTrades() {
        this.pendingTrades.forEach((tradeUpdate) => this.update(tradeUpdate));
        this.pendingTrades = [];
        this.isPending = false;
    }

    private update(data: Trade) {
        const row = this.gridApi.getRowNode(data.id);
        let tradeData;
        if (row) {
            tradeData = {
                update: [Object.assign(row.data, { state: data.state })]
            };
        } else {
            tradeData = {
                add: [{
                    accountid: data.accountid,
                    created: data.created,
                    id: data.id,
                    quantity: data.quantity,
                    security: data.security,
                    side: data.side,
                    state: data.state,
                    updated: data.updated,
                    unitPrice: data.unitPrice
                }],
                addIndex: 0
            };
        }
        this.gridApi.applyTransaction(tradeData);
    }

    private updateTrades(data: Trade) {
        if (this.isPending) {
            this.pendingTrades.push(data);
        } else {
            this.update(data);
        }
    }
}
