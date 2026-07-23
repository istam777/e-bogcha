import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { FilterBar } from '../ui/FilterBar';
import { LeadTable } from '../ui/LeadTable';
import { LeadCards } from '../ui/LeadCards';
import { Pagination } from '../ui/Pagination';
import { EmptyState } from '../ui/EmptyState';
import { TableRowSkeleton, CardSkeleton } from '@/shared/ui/Skeleton';
import { ErrorDisplay } from '@/shared/ui/ErrorDisplay';
import { useActor } from '@/app/providers/useActor';
import { fetchLeads } from '../api/leads-api';
import {
  searchParamsToParams,
  paramsToSearchParams,
  localDateToApiInstant,
} from '../lib/url-state';
import {
  hasActiveFilters,
  countActiveFilters,
} from '../api/query-params';
import { isQueryEnabled, validateFilters } from '../lib/filter-validation';
import type { LeadSearchParams } from '@/shared/types/api';

export function LeadListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { actorId, resetActor } = useActor();

  const params = useMemo(() => searchParamsToParams(searchParams), [searchParams]);

  const handleParamsChange = useCallback(
    (newParams: LeadSearchParams) => {
      const sp = paramsToSearchParams(newParams);
      setSearchParams(sp, { replace: true });
    },
    [setSearchParams],
  );

  const apiParams = useMemo(() => {
    const p = { ...params };
    if (p.createdFrom) p.createdFrom = localDateToApiInstant(p.createdFrom, 'start');
    if (p.createdTo) p.createdTo = localDateToApiInstant(p.createdTo, 'end');
    return p;
  }, [params]);

  const filterErrors = useMemo(() => validateFilters(params), [params]);
  const queryEnabled = useMemo(() => isQueryEnabled(!!actorId, params), [actorId, params]);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['leads', apiParams, actorId],
    queryFn: ({ signal }) => fetchLeads(apiParams, actorId, signal),
    enabled: queryEnabled,
    staleTime: 30_000,
  });

  const activeFilterCount = useMemo(() => countActiveFilters(params), [params]);
  const isFiltered = useMemo(() => hasActiveFilters(params), [params]);

  if (error) {
    return (
      <div className="page-content">
        <header className="page-header">
          <h1 className="page-title">CRM Leadlar</h1>
          <p className="page-subtitle">Barcha leadlarni qidirish, filtrlash va kuzatish</p>
        </header>
        <ErrorDisplay
          error={error as Error}
          onRetry={() => refetch()}
          onActorReset={resetActor}
        />
      </div>
    );
  }

  return (
    <div className="page-content">
      <header className="page-header">
        <h1 className="page-title">CRM Leadlar</h1>
        <p className="page-subtitle">Barcha leadlarni qidirish, filtrlash va kuzatish</p>
      </header>

      <FilterBar
        params={params}
        onChange={handleParamsChange}
        activeFilterCount={activeFilterCount}
      />

      {!filterErrors.valid && (
        <div className="filter-validation-errors" role="alert">
          {filterErrors.errors.map((err) => (
            <span key={err} className="filter-validation-error">{err}</span>
          ))}
        </div>
      )}

      {isLoading ? (
        <>
          <div className="table-wrapper skeleton-table" aria-label="Yuklanmoqda" role="status">
            <table className="lead-table" aria-hidden="true">
              <thead>
                <tr>
                  <th>Ota-ona / vasiy</th>
                  <th>Telefon</th>
                  <th>Manba</th>
                  <th>Holat</th>
                  <th>Filial</th>
                  <th>Mas'ul operator</th>
                  <th>Yaratilgan vaqt</th>
                  <th>Aloqa muddati</th>
                </tr>
              </thead>
              <tbody>
                {Array.from({ length: 5 }, (_, i) => (
                  <TableRowSkeleton key={i} />
                ))}
              </tbody>
            </table>
          </div>
          <div className="mobile-skeleton" aria-hidden="true">
            {Array.from({ length: 3 }, (_, i) => (
              <CardSkeleton key={i} />
            ))}
          </div>
        </>
      ) : data && data.items.length > 0 ? (
        <>
          <LeadTable leads={data.items} />
          <LeadCards leads={data.items} />
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            hasNext={data.hasNext}
            hasPrevious={data.hasPrevious}
            onPageChange={(p) => handleParamsChange({ ...params, page: p })}
          />
        </>
      ) : (
        <EmptyState hasFilters={isFiltered} onClearFilters={() => handleParamsChange({ q: '', page: 0, size: params.size })} />
      )}
    </div>
  );
}
