import { ReactNode } from 'react'

interface Props {
  columnas: string[]
  vacio?: string
  children: ReactNode
  hayFilas: boolean
}

export default function Table({ columnas, vacio = 'Sin registros.', children, hayFilas }: Props) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50">
          <tr>
            {columnas.map((c, i) => (
              <th key={`${c}-${i}`} className="px-4 py-2 text-left font-semibold text-slate-600">{c}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {hayFilas ? children : (
            <tr><td colSpan={columnas.length} className="px-4 py-6 text-center text-slate-500">{vacio}</td></tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

export const Td = ({ children, className = '' }: { children?: ReactNode; className?: string }) => (
  <td className={`px-4 py-2 text-slate-700 ${className}`}>{children}</td>
)
