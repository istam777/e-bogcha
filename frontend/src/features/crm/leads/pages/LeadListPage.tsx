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
import { useActor } from '@/app/providers/ActorProvider';
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
import type { LeadSearchParams } from '@/shared/types/api';

export function LeadListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { actorId, resetActor } = useActor();

  const params = useMemo(() => searchParamsToParams(searchParams), [searchParams]);

  const handleParamsChange = useCallback(
    (newParams: LeadSearchParams) => {
      const apiParams: LeadSearchParams = { ...newParams };

      if (newParams.createdFrom) {
        apiParams.createdFrom = localDateToApiInstant(newParams.createdFrom, 'start');
      }
      if (newParams.createdTo) {
        apiParams.createdTo = localDateToApiInstant(newParams.createdTo, 'end');
      }

      const sp = paramsToSearchParams(apiParams);
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

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['leads', apiParams, actorId],
    queryFn: ({ signal }) => fetchLeads(apiParams, actorId, signal),
    enabled: !!actorId,
    staleTime: 30_000,
  });

  const activeFilterCount = useMemo(() => countActiveFilters(params), [params]);
  const isFiltered = useMemo(() => hasActiveFilters(params), [params]);

  if (error) {
    return (
      <div className="page-content">
        <header className="page-header">
          <h1 className="page-title">Leadlar</h1>
          <p className="page-subtitle">CRM orqali kelgan murojaatlar va ularning holati</p>
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
        <h1 className="page-title">Leadlar</h1>
        <p className="page-subtitle">CRM orqali kelgan murojaatlar va ularning holati</p>
      </header>

      <FilterBar
        params={params}
        onChange={handleParamsChange}
        activeFilterCount={activeFilterCount}
      />

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
