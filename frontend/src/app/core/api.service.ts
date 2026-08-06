import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../environments/environment';

export interface Company {
  id: string;
  cnpj: string;
  legalName: string;
  tradeName: string;
  cnaeMain: string;
  cnaeSecondary?: string;
  cnaeDescription: string;
  city: string;
  state: string;
  neighborhood: string;
  street?: string;
  zipCode?: string;
  capitalSocial: number;
  openedAt: string;
  estimatedRevenue: string;
  website: string;
  email?: string;
  phone?: string;
  latitude: number;
  longitude: number;
  geocoded?: boolean;
  locationPrecision?: string;
  webContactable?: boolean;
}

export interface Professional {
  id: string;
  name: string;
  occupation: string;
  specialties?: string;
  bio?: string;
  email?: string;
  whatsapp?: string;
  phone?: string;
  emailAvailable: boolean;
  whatsappAvailable: boolean;
  phoneAvailable: boolean;
  website?: string;
  instagram?: string;
  profileImageUrl?: string;
  rating?: number;
  reviewCount: number;
  yearsExperience?: number;
  verified: boolean;
  serviceMode: string;
  neighborhood?: string;
  city: string;
  state: string;
  latitude: number;
  longitude: number;
  distanceKm: number;
  source: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface CnaeActivityOption {
  code: string;
  label: string;
  filterValue: string;
  kind?: 'SECTION' | 'DIVISION' | 'SUBCLASS';
}

export interface CnaeSectionCatalog {
  sectionCode: string;
  title: string;
  searchHint: string;
  sectionFilterValue: string;
  activities: CnaeActivityOption[];
}

export interface Contact {
  id: string;
  fullName: string;
  roleTitle: string;
  email: string;
  phone: string;
  whatsapp: string;
  linkedinUrl: string;
  websiteUrl?: string;
  instagramUrl?: string;
  confidence: number;
  source: string;
}

export interface Template {
  id: string;
  name: string;
  channel: string;
  subject: string;
  bodyTemplate: string;
}

export interface Campaign {
  id: string;
  name: string;
  channel: string;
  status: string;
  sentCount: number;
  createdAt: string;
}

export interface ApproachStatus {
  companyId: string;
  leadId: string | null;
  leadStatus: string | null;
  approached: boolean;
  lastChannel: string | null;
  lastProvider: string | null;
  lastRecipient: string | null;
  lastSentAt: string | null;
  lastErrorDetail: string | null;
  wasFallback: boolean;
  lastCampaignName: string | null;
}

export interface OutreachMessageHistoryItem {
  id: string;
  campaignId: string;
  campaignName: string;
  channel: string;
  provider: string;
  recipient: string;
  status: string;
  subject: string | null;
  bodyPreview: string | null;
  sentAt: string | null;
  createdAt: string;
  errorDetail: string | null;
  wasFallback: boolean;
}

export interface DeliveryItem {
  companyId: string;
  companyName: string;
  status: string;
  channel: string | null;
  provider: string | null;
  recipient: string | null;
  fallback: boolean;
  detail: string | null;
}

export interface ChannelStatus {
  emailConfigured: boolean;
  emailTestMode: boolean;
  emailTestAddress: string;
  emailLabel: string;
  whatsappEnabled: boolean;
  whatsappConnected: boolean;
  whatsappLabel: string;
  whatsappInstance: string;
  whatsappRawState: string | null;
}

export interface WhatsAppConnection {
  providerEnabled: boolean;
  connected: boolean;
  status: string;
  statusLabel: string;
  instanceName: string | null;
  phone: string | null;
  qrCodeBase64: string | null;
  hint: string;
  connectedAt: string | null;
}

export interface OutreachSettings {
  senderName: string | null;
  hasBrandImage: boolean;
  brandImageMime: string | null;
  brandImageFileName: string | null;
  brandImageBase64: string | null;
  approvalRecipient1: string | null;
  approvalRecipient2: string | null;
}

export interface ProspectJob {
  id: string;
  name: string;
  cnae: string | null;
  state: string | null;
  city: string | null;
  keyword: string | null;
  companyLimit: number;
  status: string;
  testMode: boolean;
  dryRun: boolean;
  foundCount: number;
  enrichedCount: number;
  queuedCount: number;
  waSent: number;
  emailSent: number;
  failedCount: number;
  errorDetail: string | null;
  nextDispatchAt: string | null;
  waPausedUntil: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  campaignId: string | null;
}

export interface OutreachReport {
  firstStepSent: number;
  repliesReceived: number;
  followUpsAwaitingApproval: number;
  followUpsSent: number;
  followUpsFailed: number;
}

export interface CampaignMessageDetail {
  id: string; companyId: string; companyName: string; cnpj: string; city: string; state: string;
  contactName: string | null; contactRole: string | null; recipient: string | null; email: string | null;
  step: number; channel: string; status: string; subject: string | null; body: string;
  createdAt: string; sentAt: string | null; repliedAt: string | null; approvedAt: string | null;
  providerMessageId: string | null; errorDetail: string | null; editable: boolean; retryable: boolean;
}

export interface CampaignDetail extends Campaign {
  followUpBody: string | null; total: number; queued: number; sent: number; waitingReply: number;
  replied: number; awaitingApproval: number; step2Queued: number; step2Sent: number;
  failed: number; skipped: number; messages: CampaignMessageDetail[];
}

export interface OutreachBotStatus {
  connected: boolean;
  paused: boolean;
  queue: number;
  step1Queue: number;
  step2Queue: number;
  processing: number;
  deadLetter: number;
  pendingEvents: number;
  sentToday: number;
  remainingToday: number;
  restrictionDetected: boolean;
  deliveryEnabled: boolean;
  stage2ApprovalRequired: boolean;
  coldOpened: number;
  repliesReceived: number;
  step2Sent: number;
  failed: number;
  nextColdAt: number | null;
  estimatedDays: number;
  cadence: { minSeconds: number; maxSeconds: number; dailyCap: number };
}

export interface FollowUpReviewItem {
  id: string;
  companyId: string;
  companyName: string;
  recipient: string;
  body: string;
  createdAt: string;
}

export interface ApproachVariant {
  id: string;
  label: string;
  description: string;
  greeting: string;
  body: string;
  subject?: string | null;
}

export interface AiCopyResult {
  subject: string | null;
  body: string;
  channel: string;
  greeting?: string | null;
  editableBody?: string | null;
  selectedApproachId?: string | null;
  approaches?: ApproachVariant[];
}

export interface TestEmailResult {
  success: boolean;
  deliveredTo: string | null;
  subject: string | null;
  message: string | null;
  error: string | null;
}

export interface KanbanBoard {
  pipelineId: string;
  pipelineName: string;
  stages: KanbanStage[];
}

export interface KanbanStage {
  id: string;
  name: string;
  position: number;
  color: string;
  cards: KanbanCard[];
}

export interface KanbanCard {
  id: string;
  leadId: string;
  title: string;
  companyName: string;
  city: string;
  state: string;
  valueAmount: number;
  position: number;
  createdAt?: string;
  ownerName?: string;
}

export interface ClientListItem {
  id: string;
  displayName: string;
  document: string;
  city: string;
  state: string;
  status: string;
  serviceStatus: string;
  lifetimeValue: number;
  tenureDays: number;
  ownerName?: string;
  email?: string;
  phone?: string;
}

export interface PortfolioStats {
  total: number;
  empty: boolean;
  activeCount?: number;
  totalLtv?: number;
  avgLtv?: number;
}

export interface CreateClientPayload {
  legalName: string;
  tradeName?: string;
  document: string;
  email?: string;
  phone?: string;
  city: string;
  state: string;
  initialValue?: number;
}

export interface Client360 extends ClientListItem {
  legalName: string;
  tradeName?: string;
  email?: string;
  phone?: string;
  contractedAt: string;
  contactHistory: { id: string; channel: string; subject?: string; status: string; sentAt?: string; repliedAt?: string }[];
  proposals: { id: string; title: string; status: string; totalAmount: number; createdAt: string }[];
  services: { id: string; name: string; status: string; progressPercent: number }[];
}

export interface Proposal {
  id: string;
  leadId?: string;
  title: string;
  totalAmount: number;
  status: string;
  paymentTerms?: string;
  validityDays: number;
  approvalToken: string;
  approvalUrl: string;
  createdAt: string;
  items: { id: string; description: string; quantity: number; unitPrice: number }[];
}

export interface CreateProposalPayload {
  leadId?: string;
  clientId?: string;
  companyId?: string;
  title: string;
  paymentTerms?: string;
  validityDays?: number;
  items: { description: string; quantity: number; unitPrice: number }[];
}

export interface ProposalRecipientOption {
  key: string;
  label: string;
  subtitle: string;
  phone?: string;
  source: string;
}

export interface PublicProposal {
  companyName: string;
  title: string;
  totalAmount: number;
  paymentTerms?: string;
  expiresAt: string;
  status: string;
  items: { description: string; quantity: number; unitPrice: number }[];
}

export interface ProjectBoard {
  columns: {
    status: string;
    label: string;
    projects: {
      id: string;
      name: string;
      status: string;
      progressPercent: number;
      clientName: string;
      dueAt?: string;
    }[];
  }[];
}

export interface PublicPortal {
  clientName: string;
  projectName: string;
  status: string;
  progressPercent: number;
  timeline: { title: string; description?: string; status: string; completedAt?: string }[];
  deliverables: string[];
}

export interface AlertItem {
  id: string;
  companyId?: string;
  companyName: string;
  alertType: string;
  title: string;
  description: string;
  read: boolean;
  triggeredAt: string;
}

export interface AppointmentItem {
  id: string;
  clientId?: string;
  clientName: string;
  clientEmail?: string;
  clientPhone?: string;
  clientCompany?: string;
  title: string;
  description?: string;
  location?: string;
  videoConference: boolean;
  meetingUrl?: string;
  startsAt: string;
  endsAt?: string;
  reminderMinutesBefore: number;
  reminderSent: boolean;
  status: string;
  ownerName?: string;
  createdAt: string;
}

export interface CreateAppointmentPayload {
  clientId?: string;
  clientName: string;
  clientEmail?: string;
  clientPhone?: string;
  clientCompany?: string;
  title: string;
  description?: string;
  location?: string;
  videoConference?: boolean;
  meetingUrl?: string;
  startsAt: string;
  endsAt?: string;
  reminderMinutesBefore?: number;
}

export type UpdateAppointmentPayload = CreateAppointmentPayload;

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private readonly http: HttpClient) {}

  searchCompanies(filters: {
    keyword?: string;
    cnae?: string;
    state?: string;
    city?: string;
    revenue?: string;
    activeOnly?: boolean;
    contactableOnly?: boolean;
    page?: number;
    size?: number;
  }) {
    let params = new HttpParams();
    Object.entries(filters).forEach(([key, value]) => {
      if (value === undefined || value === null) {
        return;
      }
      if (typeof value === 'boolean') {
        params = params.set(key, String(value));
        return;
      }
      if (`${value}`.length > 0) {
        params = params.set(key, `${value}`);
      }
    });
    return this.http.get<Page<Company>>(`${environment.apiUrl}/discovery/companies`, { params });
  }

  refineCompanyCoordinates(companyIds: string[]) {
    return this.http.post<Company[]>(`${environment.apiUrl}/discovery/companies/refine-coordinates`, {
      companyIds
    });
  }

  getCnaeCatalog() {
    return this.http.get<CnaeSectionCatalog[]>(`${environment.apiUrl}/discovery/cnaes`);
  }

  searchCnaeActivities(query: string, limit = 40) {
    return this.http.get<CnaeActivityOption[]>(`${environment.apiUrl}/discovery/cnaes/search`, {
      params: { q: query, limit }
    });
  }

  listCnaeSubclasses(prefix: string, limit = 200) {
    return this.http.get<CnaeActivityOption[]>(`${environment.apiUrl}/discovery/cnaes/subclasses`, {
      params: { prefix, limit }
    });
  }

  searchGeo(latitude: number, longitude: number, radiusKm = 10, cnae?: string) {
    let params = new HttpParams()
      .set('latitude', latitude)
      .set('longitude', longitude)
      .set('radiusKm', radiusKm);
    if (cnae) {
      params = params.set('cnae', cnae);
    }
    return this.http.get<Company[]>(`${environment.apiUrl}/discovery/companies/geo`, { params });
  }

  searchProfessionals(query: string, latitude: number, longitude: number, radiusKm = 10) {
    const params = new HttpParams()
      .set('query', query)
      .set('latitude', latitude)
      .set('longitude', longitude)
      .set('radiusKm', radiusKm);
    return this.http.get<Professional[]>(`${environment.apiUrl}/professionals/search`, { params });
  }

  resolveProfessionalSearchLocation(location: string) {
    return this.http.get<{ latitude: number; longitude: number; label: string }>(
      `${environment.apiUrl}/professionals/location`, { params: { location } }
    );
  }

  claimProfessionalContact(id: string, channel: 'WHATSAPP' | 'EMAIL' | 'PHONE') {
    return this.http.post<{ channel: string; value: string; freeContactConsumed: boolean }>(
      `${environment.apiUrl}/professionals/${id}/contact`, {}, { params: { channel } }
    );
  }

  getCompaniesByIds(ids: string[]) {
    if (ids.length === 0) {
      return this.http.get<Company[]>(`${environment.apiUrl}/discovery/companies/by-ids`, {
        params: { ids: '' }
      });
    }
    return this.http.get<Company[]>(`${environment.apiUrl}/discovery/companies/by-ids`, {
      params: { ids: ids.join(',') }
    });
  }

  enrich(companyIds: string[], forceRefresh = false) {
    return this.http.post<Contact[]>(`${environment.apiUrl}/enrichment/enrich`, { companyIds, forceRefresh });
  }

  listContacts(companyId: string) {
    return this.http.get<Contact[]>(`${environment.apiUrl}/enrichment/companies/${companyId}/contacts`);
  }

  templates() {
    return this.http.get<Template[]>(`${environment.apiUrl}/outreach/templates`);
  }

  campaigns() {
    return this.http.get<Campaign[]>(`${environment.apiUrl}/outreach/campaigns`);
  }

  outreachReport() {
    return this.http.get<OutreachReport>(`${environment.apiUrl}/outreach/report`);
  }

  campaignDetail(id: string) { return this.http.get<CampaignDetail>(`${environment.apiUrl}/outreach/campaigns/${id}`); }
  updateCampaign(id: string, payload: { name?: string; followUpBody?: string }) {
    return this.http.put<CampaignDetail>(`${environment.apiUrl}/outreach/campaigns/${id}`, payload);
  }
  pauseCampaign(id: string) { return this.http.post<CampaignDetail>(`${environment.apiUrl}/outreach/campaigns/${id}/pause`, {}); }
  resumeCampaign(id: string) { return this.http.post<CampaignDetail>(`${environment.apiUrl}/outreach/campaigns/${id}/resume`, {}); }
  updateCampaignMessage(campaignId: string, messageId: string, payload: { subject?: string; body: string }) {
    return this.http.put<CampaignMessageDetail>(`${environment.apiUrl}/outreach/campaigns/${campaignId}/messages/${messageId}`, payload);
  }
  retryCampaignMessage(campaignId: string, messageId: string) {
    return this.http.post<CampaignMessageDetail>(`${environment.apiUrl}/outreach/campaigns/${campaignId}/messages/${messageId}/retry`, {});
  }

  outreachBotStatus() {
    return this.http.get<OutreachBotStatus>(`${environment.apiUrl}/outreach/bot/status`);
  }

  pauseOutreachBot() {
    return this.http.post<OutreachBotStatus>(`${environment.apiUrl}/outreach/bot/pause`, {});
  }

  resumeOutreachBot() {
    return this.http.post<OutreachBotStatus>(`${environment.apiUrl}/outreach/bot/resume`, {});
  }

  followUpsAwaitingApproval() {
    return this.http.get<FollowUpReviewItem[]>(`${environment.apiUrl}/outreach/follow-ups`);
  }

  approveFollowUp(id: string) {
    return this.http.post<{ id: string; status: string; error: string | null }>(
      `${environment.apiUrl}/outreach/follow-ups/${id}/approve`,
      {}
    );
  }

  publicFollowUp(token: string) {
    return this.http.get<FollowUpReviewItem>(`${environment.apiUrl}/public/outreach/approvals/${token}`);
  }

  approvePublicFollowUp(token: string) {
    return this.http.post<{ id: string; status: string; error: string | null }>(
      `${environment.apiUrl}/public/outreach/approvals/${token}`,
      {}
    );
  }

  generateAiCopy(payload: { companyId: string; channel: string; productDescription: string; tone?: string }) {
    return this.http.post<AiCopyResult>(`${environment.apiUrl}/outreach/ai-copy`, payload);
  }

  sendBulk(payload: {
    campaignName: string;
    templateId: string;
    channel: string;
    companyIds: string[];
    productDescription?: string;
    messages?: Record<string, { subject?: string; body?: string }>;
    emailFallback?: boolean;
    approachId?: string;
    editableBody?: string;
    editableSubject?: string;
    followUpBody?: string;
  }) {
    return this.http.post<
      Campaign & {
        failedCount?: number;
        detail?: string;
        waSent?: number;
        emailSent?: number;
        nonWhatsApp?: string[];
        deliveries?: DeliveryItem[];
      }
    >(`${environment.apiUrl}/outreach/campaigns/bulk`, payload);
  }

  approachStatus(companyIds: string[]) {
    return this.http.get<ApproachStatus[]>(`${environment.apiUrl}/crm/approach-status`, {
      params: { companyIds: companyIds.join(',') }
    });
  }

  companyOutreachMessages(companyId: string) {
    return this.http.get<OutreachMessageHistoryItem[]>(`${environment.apiUrl}/outreach/companies/${companyId}/messages`);
  }

  outreachChannels() {
    return this.http.get<ChannelStatus>(`${environment.apiUrl}/outreach/channels`);
  }

  whatsappStatus() {
    return this.http.get<WhatsAppConnection>(`${environment.apiUrl}/whatsapp/status`);
  }

  whatsappConnect() {
    return this.http.post<WhatsAppConnection>(`${environment.apiUrl}/whatsapp/connect`, {});
  }

  whatsappRefreshQr() {
    return this.http.post<WhatsAppConnection>(`${environment.apiUrl}/whatsapp/qr`, {});
  }

  whatsappDisconnect() {
    return this.http.post<WhatsAppConnection>(`${environment.apiUrl}/whatsapp/disconnect`, {});
  }

  getOutreachSettings() {
    return this.http.get<OutreachSettings>(`${environment.apiUrl}/tenant/outreach-settings`);
  }

  updateOutreachSettings(payload: {
    senderName?: string | null;
    brandImageBase64?: string | null;
    brandImageMime?: string | null;
        brandImageFileName?: string | null;
        clearBrandImage?: boolean;
        approvalRecipient1?: string | null;
        approvalRecipient2?: string | null;
  }) {
    return this.http.put<OutreachSettings>(`${environment.apiUrl}/tenant/outreach-settings`, payload);
  }

  whatsappConfigureWebhook() {
    return this.http.post<WhatsAppConnection>(`${environment.apiUrl}/whatsapp/webhook`, {});
  }

  testApprovalNotification() {
    return this.http.post<{ configuredCount: number; sentCount: number; error: string | null }>(
      `${environment.apiUrl}/tenant/outreach-settings/test-approval-notification`, {}
    );
  }

  sendWhatsAppMessage(payload: { phone: string; message: string; clientId?: string }) {
    return this.http.post<{
      success: boolean;
      phone: string;
      messageId: string | null;
      error: string | null;
    }>(`${environment.apiUrl}/whatsapp/send`, payload);
  }

  sendTestEmail() {
    return this.http.post<TestEmailResult>(`${environment.apiUrl}/outreach/test-email`, {});
  }

  sendTestWhatsApp(phone?: string) {
    return this.http.post<{
      success: boolean;
      phone: string;
      instance: string;
      mode: string;
      messageId: string | null;
      preview: string | null;
      error: string | null;
      qrCodeBase64: string | null;
    }>(`${environment.apiUrl}/outreach/test-whatsapp`, { phone: phone || null });
  }

  startProspectJob(payload: {
    name?: string;
    cnae?: string;
    state?: string;
    city?: string;
    keyword?: string;
    companyLimit?: number;
    testMode?: boolean;
    dryRun?: boolean;
    selectedCompanyIds?: string[];
    openingMessage?: string;
  }) {
    return this.http.post<ProspectJob>(`${environment.apiUrl}/prospect/jobs`, payload);
  }

  listProspectJobs() {
    return this.http.get<ProspectJob[]>(`${environment.apiUrl}/prospect/jobs`);
  }

  getProspectJob(id: string) {
    return this.http.get<ProspectJob>(`${environment.apiUrl}/prospect/jobs/${id}`);
  }

  pauseProspectJob(id: string) {
    return this.http.post<ProspectJob>(`${environment.apiUrl}/prospect/jobs/${id}/pause`, {});
  }

  resumeProspectJob(id: string) {
    return this.http.post<ProspectJob>(`${environment.apiUrl}/prospect/jobs/${id}/resume`, {});
  }

  board() {
    return this.http.get<KanbanBoard>(`${environment.apiUrl}/crm/board`);
  }

  moveCard(cardId: string, stageId: string, position: number) {
    return this.http.patch<KanbanCard>(`${environment.apiUrl}/crm/cards/${cardId}/move`, { stageId, position });
  }

  listClients(params?: { status?: string; serviceStatus?: string; minLtv?: number; tenureDays?: number }) {
    return this.http.get<ClientListItem[]>(`${environment.apiUrl}/clients`, { params: params as Record<string, string | number> });
  }

  getClientPortfolioStats() {
    return this.http.get<PortfolioStats>(`${environment.apiUrl}/clients/stats`);
  }

  createClient(payload: CreateClientPayload) {
    return this.http.post<ClientListItem>(`${environment.apiUrl}/clients`, payload);
  }

  getClient(id: string) {
    return this.http.get<Client360>(`${environment.apiUrl}/clients/${id}`);
  }

  listProposals() {
    return this.http.get<Proposal[]>(`${environment.apiUrl}/proposals`);
  }

  listProposalRecipients(search?: string) {
    const params = search ? { search } : undefined;
    return this.http.get<ProposalRecipientOption[]>(`${environment.apiUrl}/proposals/recipients`, { params });
  }

  createProposal(payload: CreateProposalPayload) {
    return this.http.post<Proposal>(`${environment.apiUrl}/proposals`, payload);
  }

  publishProposal(id: string) {
    return this.http.post<Proposal>(`${environment.apiUrl}/proposals/${id}/publish`, {});
  }

  getProjectBoard() {
    return this.http.get<ProjectBoard>(`${environment.apiUrl}/projects/board`);
  }

  updateProjectStatus(projectId: string, status: string) {
    return this.http.patch<unknown>(`${environment.apiUrl}/projects/${projectId}/status`, { status });
  }

  getPublicProposal(token: string) {
    return this.http.get<PublicProposal>(`${environment.apiUrl}/public/proposals/${token}`);
  }

  approvePublicProposal(token: string, payload: { signerName: string; signerDocument: string }) {
    return this.http.post<{ status: string }>(`${environment.apiUrl}/public/proposals/${token}/approve`, payload);
  }

  getPublicPortal(token: string) {
    return this.http.get<PublicPortal>(`${environment.apiUrl}/public/portal/${token}`);
  }

  alerts() {
    return this.http.get<AlertItem[]>(`${environment.apiUrl}/alerts`);
  }

  markAlertRead(alertId: string) {
    return this.http.patch<AlertItem>(`${environment.apiUrl}/alerts/${alertId}/read`, {});
  }

  listAppointments(params?: { from?: string; to?: string }) {
    return this.http.get<AppointmentItem[]>(`${environment.apiUrl}/appointments`, { params: params as Record<string, string> });
  }

  getAppointment(id: string) {
    return this.http.get<AppointmentItem>(`${environment.apiUrl}/appointments/${id}`);
  }

  createAppointment(payload: CreateAppointmentPayload) {
    return this.http.post<AppointmentItem>(`${environment.apiUrl}/appointments`, payload);
  }

  updateAppointment(id: string, payload: UpdateAppointmentPayload) {
    return this.http.patch<AppointmentItem>(`${environment.apiUrl}/appointments/${id}`, payload);
  }

  cancelAppointment(id: string) {
    return this.http.delete<void>(`${environment.apiUrl}/appointments/${id}`);
  }
}
