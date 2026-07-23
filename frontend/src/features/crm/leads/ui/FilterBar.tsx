import { useCallback, useMemo, useRef, useEffect } from 'react';
import { Filter, X } from 'lucide-react';
import { SearchInput } from './SearchInput';
import { Select } from '@/shared/ui/Select';
import { Switch } from '@/shared/ui/Switch';
import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';
import {
  STATUS_OPTIONS,
  SOURCE_OPTIONS,
  OWNER_STATE_OPTIONS,
  PAGE_SIZE_OPTIONS,
} from '../model/labels';
import type { LeadSearchParams, OwnerState } from '@/shared/types/api';
import { isValidUuid } from '@/shared/lib/actor';

interface FilterBarProps {
  params: LeadSearchParams;
  onChange: (params: LeadSearchParams) => void;
  activeFilterCount: number;
}

export function FilterBar({ params, onChange, activeFilterCount }: FilterBarProps) {
  const updateParam = useCallback(
    <K extends keyof LeadSearchParams>(key: K, value: LeadSearchParams[K]) => {
      const next = { ...params, [key]: value, page: 0 };
      if (key !== 'page') next.page = 0;
      onChange(next);
    },
    [params, onChange],
  );

  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleSearchChange = useCallback(
    (q: string) => {
      if (timerRef.current) clearTimeout(timerRef.current);
      if (q.length > 0 && q.length < 2) return;

      timerRef.current = setTimeout(() => {
        onChange({ ...params, q, page: 0 });
      }, 350);
    },
    [params, onChange],
  );

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  const handleClearFilters = useCallback(() => {
    onChange({
      q: '',
      status: undefined,
      source: undefined,
      ownerState: 'ALL',
      overdueOnly: false,
      createdFrom: undefined,
      createdTo: undefined,
      branchId: undefined,
      ownerOperatorId: undefined,
      page: 0,
      size: params.size,
    });
  }, [params.size, onChange]);

  const ownerStateWarning = useMemo(() => {
    if (params.ownerOperatorId && params.ownerState === 'UNASSIGNED') {
      return 'Biriktirilgan operator va "Biriktirilmagan" filtri birga ishlatilmaydi';
    }
    return null;
  }, [params.ownerOperatorId, params.ownerState]);

  const branchIdError = useMemo(() => {
    if (params.branchId && !isValidUuid(params.branchId)) {
      return "Noto'g'ri UUID formati";
    }
    return undefined;
  }, [params.branchId]);

  const ownerOpIdError = useMemo(() => {
    if (params.ownerOperatorId && !isValidUuid(params.ownerOperatorId)) {
      return "Noto'g'ri UUID formati";
    }
    return undefined;
  }, [params.ownerOperatorId]);

  return (
    <div className="filter-bar">
      <div className="filter-bar__search">
        <SearchInput value={params.q || ''} onChange={handleSearchChange} />
      </div>

      <div className="filter-bar__main">
        <Select
          label="Holat"
          options={STATUS_OPTIONS}
          value={params.status || ''}
          onChange={(e) => updateParam('status', e.target.value as LeadSearchParams['status'])}
          placeholder="Barcha holatlar"
        />

        <Select
          label="Manba"
          options={SOURCE_OPTIONS}
          value={params.source || ''}
          onChange={(e) => updateParam('source', e.target.value as LeadSearchParams['source'])}
          placeholder="Barcha manbalar"
        />

        <Select
          label="Egasi holati"
          options={OWNER_STATE_OPTIONS}
          value={params.ownerState || 'ALL'}
          onChange={(e) => updateParam('ownerState', e.target.value as OwnerState)}
        />

        <Switch
          label="Faqat kechikkanlar"
          checked={!!params.overdueOnly}
          onChange={(checked) => updateParam('overdueOnly', checked)}
        />

        <Input
          label="Yaratilgan (dan)"
          type="date"
          value={params.createdFrom || ''}
          onChange={(e) => updateParam('createdFrom', e.target.value || undefined)}
        />

        <Input
          label="Yaratilgan (gacha)"
          type="date"
          value={params.createdTo || ''}
          onChange={(e) => updateParam('createdTo', e.target.value || undefined)}
        />
      </div>

      <div className="filter-bar__advanced">
        <Input
          label="Filial ID"
          placeholder="UUID"
          value={params.branchId || ''}
          onChange={(e) => updateParam('branchId', e.target.value || undefined)}
          error={branchIdError}
        />

        <Input
          label="Operator ID"
          placeholder="UUID"
          value={params.ownerOperatorId || ''}
          onChange={(e) => updateParam('ownerOperatorId', e.target.value || undefined)}
          error={ownerOpIdError}
        />
      </div>

      {ownerStateWarning && (
        <div className="filter-bar__warning" role="alert">
          {ownerStateWarning}
        </div>
      )}

      <div className="filter-bar__footer">
        <div className="filter-bar__info">
          {activeFilterCount > 0 && (
            <span className="filter-bar__count">
              <Filter size={14} />
              {activeFilterCount} filtri faol
            </span>
          )}
        </div>

        <div className="filter-bar__actions">
          <Select
            options={PAGE_SIZE_OPTIONS.map((s) => ({ value: String(s), label: `${s} ta` }))}
            value={String(params.size || 20)}
            onChange={(e) => updateParam('size', parseInt(e.target.value, 10))}
            aria-label="Sahifada ko'rsatish"
          />

          {activeFilterCount > 0 && (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleClearFilters}
              aria-label="Filtrlarni tozalash"
            >
              <X size={14} />
              Filtrlarni tozalash
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
