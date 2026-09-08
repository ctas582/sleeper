import type { InstanceFeatures } from '../contexts/InstanceContext'
import type { FeatureName } from './features'

export type IngestMethod =
	| 'ingest_batcher'
	| 'standard_ingest'
	| 'bulk_import_emr_serverless'
	| 'bulk_import_emr'
	| 'bulk_import_persistent_emr'
	| 'bulk_import_eks'

export interface IngestMethodInfo {
	key: IngestMethod
	label: string
	hint: string
	feature: FeatureName
}

export const INGEST_METHODS: IngestMethodInfo[] = [
	{
		key: 'ingest_batcher',
		label: 'Ingest Batcher',
		hint: 'Sleeper holds the files and decides when to make a job. Best when files arrive a few at a time.',
		feature: 'IngestBatcherStack',
	},
	{
		key: 'bulk_import_emr_serverless',
		label: 'Bulk Import on EMR Serverless',
		hint: 'Spark on a serverless application that starts in seconds and stops when idle. Best for occasional jobs.',
		feature: 'EmrServerlessBulkImportStack',
	},
	{
		key: 'bulk_import_eks',
		label: 'Bulk Import on EKS',
		hint: 'Spark on Kubernetes using EKS. Experimental.',
		feature: 'EksBulkImportStack',
	},
	{
		key: 'standard_ingest',
		label: 'Standard Ingest',
		hint: 'Sorts and writes the files on an ECS task. Simplest, but aim for tens of millions of rows per job.',
		feature: 'IngestStack',
	},
	{
		key: 'bulk_import_persistent_emr',
		label: 'Bulk Import on persistent EMR',
		hint: 'Spark on the always-running cluster. No startup delay, but the cluster costs while it is idle.',
		feature: 'PersistentEmrBulkImportStack',
	},
	{
		key: 'bulk_import_emr',
		label: 'Bulk Import on EMR',
		hint: 'Spark on a cluster created for this job and torn down after. Adds around 10 minutes of startup.',
		feature: 'EmrBulkImportStack',
	},
]

export const INGEST_METHOD_FEATURES: FeatureName[] = INGEST_METHODS.map(m => m.feature)

export function ingestMethodLabel(method: string): string {
	return INGEST_METHODS.find(m => m.key === method)?.label ?? method
}

export function isIngestMethodEnabled(method: IngestMethodInfo, features: InstanceFeatures | null): boolean {
	return !!features?.[method.feature]
}

export function initialIngestMethod(preferred: IngestMethod, features: InstanceFeatures | null): IngestMethod {
	const preferredInfo = INGEST_METHODS.find(m => m.key === preferred)
	if (!features || (preferredInfo && isIngestMethodEnabled(preferredInfo, features))) return preferred
	return INGEST_METHODS.find(m => isIngestMethodEnabled(m, features))?.key ?? preferred
}