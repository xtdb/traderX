import { ColDef, GridApi, GridReadyEvent, Module } from 'ag-grid-community';
import { Account } from 'main/app/model/account.model';
import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { Position, StockPrice, TradeInterval, TradePointInTime } from 'main/app/model/trade.model';
import { Observable, Subject } from 'rxjs';
import { PriceService } from 'main/app/service/price.service';
import { TradeFeedService } from 'main/app/service/trade-feed.service';

@Component({
  selector: 'app-position-blotter',
  templateUrl: './position-blotter.component.html',
  styleUrls: ['./position-blotter.component.scss']
})
export class PositionBlotterComponent implements OnChanges, OnDestroy {
  @Input() account?: Account;
  @Input() accountPositions: Position[];
  @Input() interval?: TradeInterval;
  @Input() priceDate?: Date;
  gridApi: GridApi;
  marketValueUnSubscribeFn: Function;
  title = 'Positions';
  height = '350px';
  width = '100%';
  positions: Position[];
  prices: StockPrice[];

  columnDefs: ColDef[] = [
    {
      field: 'security',
      headerName: 'SECURITY',
      width: 110
    },
    {
      headerName: 'QUANTITY',
      field: 'quantity',
      enableCellChangeFlash: true,
      width: 130
    },
    {
      headerName: 'MONEY IN/OUT',
      field: 'value',
      width: 140
    },
    {
      headerName: 'MARKET VALUE',
      field: 'marketValue',
      enableCellChangeFlash: true,
      width: 140
    },
    {
      headerName: 'CALCULATION',
      field: 'calculation',
      width: 250
    }
  ];

  constructor(private priceService: PriceService,
    private tradeFeed: TradeFeedService) { }

  ngOnChanges(change: SimpleChanges) {
    console.log('Position blotter changes...', change);
    if (change.account?.currentValue &&
        change.account.currentValue !== change.account.previousValue) {
      this.account = change.account.currentValue;
    }
    if (change.interval?.currentValue &&
        change.interval.currentValue !== change.interval.previousValue) {
      this.interval = change.interval.currentValue;
    }
    if (change.priceDate &&
        change.priceDate.currentValue !== change.priceDate.previousValue) {
      this.priceDate = change.priceDate.currentValue;
      this.marketValueUnSubscribeFn?.();
    }
    const accountId = this.account?.id || 52355;
    const interval = this.interval;
    console.log('Position blotter', accountId, interval);
    this.priceService.getPositions(accountId, interval).subscribe((positions: Position[]) => {
      this.positions = positions.filter((p) => p.quantity > 0);
      console.log('Position blotter', this.positions);
      this.priceService.getAccountPrices(accountId, this.priceDate).subscribe((prices: StockPrice[]) => {
        this.prices = prices;
        prices.forEach((price) => {
          let position = this.positions.find((p: Position) =>
            p.security === price.ticker);
          if (position) {
            position = Object.assign(position, { marketValue: Math.abs(position.quantity * price.price) });
            this.update(position);
          }

        });
      });
      this.marketValueUnSubscribeFn?.();
      if (this.priceDate === undefined) {
        this.marketValueUnSubscribeFn = this.tradeFeed.subscribe(`/accounts/${accountId}/prices`, (data: StockPrice[]) => {
            console.log('Report Position Market value feed...', data);
            this.updateMarketValues(data);
        });
      }
    });
  }

  update(data: any) {
    const row = this.gridApi.getRowNode(data.security);
    let positionData;
    if (row) {
      if (data.quantity === 0) {
        positionData = {
          remove: [{security: data.security}]
        };
      } else {
        positionData = {
          update: [Object.assign(row.data, { quantity: data.quantity,
                                             value: data.value,
                                             calculation: data.calculation })],
        };
      }
    } else {
      positionData = {
        add: [{
          accountid: data.accountid,
          quantity: data.quantity,
          security: data.security,
          updated: data.updated,
          value: data.value,
          calculation: data.calculation
        }],
        addIndex: 0
      };
    }
    this.gridApi.applyTransaction(positionData);
  }

  updateMarketValues(data: StockPrice[]) {
    data.forEach((price) => {
      const row = this.gridApi.getRowNode(price.ticker);
      if (row) {
        const positionData = {
          update: [Object.assign(row.data, { marketValue: Math.abs(row.data.quantity * price.price) })],
        };
        this.gridApi.applyTransaction(positionData);
      }
    });
  }

  onGridReady(params: GridReadyEvent) {
    console.log('position blotter is ready...');
    this.gridApi = params.api;
  }

  getRowNodeId(data: Position) {
    return data.security;
  }

  getRowClass(params: any) {
    if (params.data.marketValue + params.data.value < 0) {
      return 'negative';
    } else if (params.data.marketValue + params.data.value > 0) {
      return 'positive';
    } else {
      return '';
    }
  }

  getRowStyle(params: any) {
    if (params.data.marketValue + params.data.value < 0) {
      return { "background-color": "rgba(226, 2, 2, 0.1)" };
    } else if (params.data.marketValue + params.data.value > 0) {
      return { "background-color": "rgba(2, 226, 2, 0.1)" };
    } else {
      return { "background-color": "rgba(226, 226, 226, 0.1)" };
   }
  }

  ngOnDestroy() {
    this.marketValueUnSubscribeFn?.();
  }

}
