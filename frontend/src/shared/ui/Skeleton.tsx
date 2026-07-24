interface SkeletonProps {
  width?: string | number;
  height?: string | number;
  className?: string;
}

export function Skeleton({ width, height, className = '' }: SkeletonProps) {
  return (
    <div
      className={`skeleton ${className}`}
      style={{ width, height }}
      aria-hidden="true"
    />
  );
}

export function TableRowSkeleton() {
  return (
    <tr className="skeleton-row" aria-hidden="true">
      <td><Skeleton width="120px" height="16px" /></td>
      <td><Skeleton width="100px" height="16px" /></td>
      <td><Skeleton width="80px" height="16px" /></td>
      <td><Skeleton width="90px" height="24px" /></td>
      <td><Skeleton width="100px" height="16px" /></td>
      <td><Skeleton width="110px" height="16px" /></td>
      <td><Skeleton width="100px" height="16px" /></td>
      <td><Skeleton width="100px" height="16px" /></td>
    </tr>
  );
}

export function CardSkeleton() {
  return (
    <div className="skeleton-card" aria-hidden="true">
      <Skeleton width="60%" height="18px" />
      <Skeleton width="40%" height="14px" />
      <Skeleton width="80%" height="14px" />
      <Skeleton width="50%" height="14px" />
    </div>
  );
}
